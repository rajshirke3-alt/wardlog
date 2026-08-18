package com.wardlog.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wardlog.app.databinding.ActivityDictionaryBinding
import com.wardlog.app.databinding.DialogAddDictionaryEntryBinding
import kotlinx.coroutines.launch

/**
 * Lets the user view, add, edit, and delete the terms and doctor names that
 * power voice-input auto-correction — no APK rebuild required. Changes here
 * are saved straight to the database and picked up by MainActivity the
 * moment this screen is closed.
 */
class DictionaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryBinding
    private lateinit var adapter: DictionaryAdapter
    private val db by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = DictionaryAdapter(
            onEdit = { entry -> showAddEditDialog(entry) },
            onDelete = { entry -> confirmDelete(entry) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        db.dictionaryDao().getAll().observe(this) { entries ->
            adapter.submitList(entries)
            binding.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabAdd.setOnClickListener { showAddEditDialog(null) }
    }

    override fun onDestroy() {
        // Push whatever the dictionary looks like right now into the live
        // correction cache so voice input reflects it immediately — even if
        // the process hosting MainActivity was retained across this screen.
        lifecycleScope.launch { MedicalTermCorrector.loadFromDatabase(db.dictionaryDao()) }
        super.onDestroy()
    }

    private fun showAddEditDialog(entry: DictionaryEntry?) {
        val dialogBinding = DialogAddDictionaryEntryBinding.inflate(LayoutInflater.from(this))

        fun applyCategoryVisibility(isDoctor: Boolean) {
            dialogBinding.aliasLayout.visibility = if (isDoctor) View.GONE else View.VISIBLE
            dialogBinding.canonicalInput.hint = if (isDoctor) {
                getString(R.string.dictionary_doctor_name_hint)
            } else {
                getString(R.string.dictionary_correct_spelling_hint)
            }
        }

        dialogBinding.categoryGroup.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            applyCategoryVisibility(checkedId == R.id.radioDoctor)
        }

        if (entry != null) {
            val isDoctor = entry.category == DictionaryEntry.CATEGORY_DOCTOR
            dialogBinding.categoryGroup.check(if (isDoctor) R.id.radioDoctor else R.id.radioTerm)
            dialogBinding.aliasInput.setText(entry.alias)
            dialogBinding.canonicalInput.setText(entry.canonical)
            applyCategoryVisibility(isDoctor)
        } else {
            dialogBinding.categoryGroup.check(R.id.radioTerm)
            applyCategoryVisibility(false)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (entry == null) R.string.dictionary_add_title else R.string.dictionary_edit_title)
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val isDoctor = dialogBinding.categoryGroup.checkedRadioButtonId == R.id.radioDoctor
                val canonical = dialogBinding.canonicalInput.text.toString().trim()
                val alias = dialogBinding.aliasInput.text.toString().trim()

                if (canonical.isEmpty()) {
                    Toast.makeText(this, "Please fill in the required field", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!isDoctor && alias.isEmpty()) {
                    Toast.makeText(this, "Please enter what it sounds like when misheard", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val category = if (isDoctor) DictionaryEntry.CATEGORY_DOCTOR else DictionaryEntry.CATEGORY_TERM
                val finalAlias = if (isDoctor) canonical else alias

                lifecycleScope.launch {
                    if (entry == null) {
                        db.dictionaryDao().insert(
                            DictionaryEntry(category = category, alias = finalAlias, canonical = canonical)
                        )
                    } else {
                        db.dictionaryDao().update(
                            entry.copy(category = category, alias = finalAlias, canonical = canonical)
                        )
                    }
                    MedicalTermCorrector.loadFromDatabase(db.dictionaryDao())
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(entry: DictionaryEntry) {
        val label = if (entry.category == DictionaryEntry.CATEGORY_DOCTOR) "Dr ${entry.canonical}" else entry.canonical
        AlertDialog.Builder(this)
            .setTitle("Remove entry?")
            .setMessage("This will remove \"$label\" from the dictionary.")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    db.dictionaryDao().delete(entry)
                    MedicalTermCorrector.loadFromDatabase(db.dictionaryDao())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
