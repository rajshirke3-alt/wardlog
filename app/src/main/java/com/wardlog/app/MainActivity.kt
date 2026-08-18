package com.wardlog.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.switchMap
import androidx.recyclerview.widget.LinearLayoutManager
import com.wardlog.app.databinding.ActivityMainBinding
import com.wardlog.app.databinding.DialogAddRecordBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordAdapter
    private val db by lazy { AppDatabase.getInstance(this) }

    private var editingRecord: Record? = null
    private var activeDialogBinding: DialogAddRecordBinding? = null
    private var voiceRecorder: VoiceRecorder? = null
    private val queryLiveData = MutableLiveData("")

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            Toast.makeText(this, "Microphone permission is needed for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    private val dictionaryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Dictionary edits are saved straight to the database as they
        // happen; refresh the in-memory correction cache here too, in case
        // this Activity instance was retained across the trip.
        lifecycleScope.launch { MedicalTermCorrector.loadFromDatabase(db.dictionaryDao()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = RecordAdapter(
            onEdit = { record -> showAddEditDialog(record) },
            onDelete = { record -> confirmDelete(record) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val recordsLiveData = queryLiveData.switchMap { q ->
            if (q.isNullOrBlank()) db.recordDao().getAll() else db.recordDao().search(q)
        }
        recordsLiveData.observe(this) { records ->
            adapter.submitList(records)
            binding.emptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.searchBox.addTextChangedListener { text ->
            queryLiveData.value = text?.toString()?.trim().orEmpty()
        }

        binding.fabAdd.setOnClickListener { showAddEditDialog(null) }
        binding.btnExportPdf.setOnClickListener { exportPdf() }
        binding.btnPrint.setOnClickListener { printRecords() }
        binding.btnDictionary.setOnClickListener {
            dictionaryLauncher.launch(Intent(this, DictionaryActivity::class.java))
        }

        // Populate the dictionary with built-in defaults on first-ever
        // launch, then load whatever it currently contains into the live
        // correction cache used by voice input.
        lifecycleScope.launch {
            AppDatabase.seedDictionaryIfEmpty(db.dictionaryDao())
            MedicalTermCorrector.loadFromDatabase(db.dictionaryDao())
        }
    }

    override fun onDestroy() {
        voiceRecorder?.release()
        super.onDestroy()
    }

    // ---------- Add / Edit dialog ----------

    private fun showAddEditDialog(record: Record?) {
        editingRecord = record
        val dialogBinding = DialogAddRecordBinding.inflate(LayoutInflater.from(this))
        activeDialogBinding = dialogBinding

        record?.let {
            dialogBinding.inputBed.setText(it.bedNumber)
            dialogBinding.inputName.setText(it.patientName)
            dialogBinding.inputConsultant.setText(it.consultant)
            dialogBinding.inputDetails.setText(it.details)
        }

        dialogBinding.btnMic.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    handleMicPressed()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    view.performClick()
                    handleMicReleased()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handleMicReleased()
                    true
                }
                else -> false
            }
        }
        // performClick needs a listener to satisfy accessibility tooling —
        // actual behavior is entirely driven by the touch listener above.
        dialogBinding.btnMic.setOnClickListener { }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (record == null) "New Record" else "Edit Record")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnDismissListener {
            stopRecordingIfActive()
            activeDialogBinding = null
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val bed = dialogBinding.inputBed.text.toString().trim()
                val name = dialogBinding.inputName.text.toString().trim()
                val consultant = dialogBinding.inputConsultant.text.toString().trim()
                val details = dialogBinding.inputDetails.text.toString().trim()

                if (bed.isEmpty() && name.isEmpty() && consultant.isEmpty() && details.isEmpty()) {
                    Toast.makeText(this, "Please fill at least one field", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val current = editingRecord
                    if (current == null) {
                        db.recordDao().insert(
                            Record(bedNumber = bed, patientName = name, consultant = consultant, details = details)
                        )
                    } else {
                        db.recordDao().update(
                            current.copy(bedNumber = bed, patientName = name, consultant = consultant, details = details)
                        )
                    }
                }
                stopRecordingIfActive()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(record: Record) {
        AlertDialog.Builder(this)
            .setTitle("Delete record?")
            .setMessage("This will permanently remove Bed ${record.bedNumber} — ${record.patientName}.")
            .setPositiveButton("Delete") { _, _ -> lifecycleScope.launch { db.recordDao().delete(record) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Voice input (press-and-hold: record while held, stop on release) ----------

    private fun handleMicPressed() {
        val recorder = voiceRecorder
        if (recorder != null && recorder.isListening) return // already recording, ignore repeat down-events

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            // The permission dialog steals the touch gesture, so the hold is
            // effectively released already by the time this resolves. Ask,
            // and let the person press-and-hold again once it's granted.
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun handleMicReleased() {
        stopRecordingIfActive()
    }

    private fun startRecording() {
        val dialogBinding = activeDialogBinding ?: return
        dialogBinding.btnMic.text = getString(R.string.recording_hold_status)
        dialogBinding.voiceStatus.text = getString(R.string.listening_status)

        voiceRecorder = VoiceRecorder(
            context = this,
            onLiveTextUpdate = { liveText ->
                runOnUiThread {
                    activeDialogBinding?.voiceStatus?.text = "🎤 $liveText"
                }
            },
            onFinalText = { finalText ->
                runOnUiThread {
                    resetMicButton()
                    if (finalText.isNotBlank()) applyVoiceText(finalText)
                }
            },
            onError = { message ->
                runOnUiThread {
                    resetMicButton()
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        ).also { it.start() }
    }

    private fun stopRecordingIfActive() {
        voiceRecorder?.let { if (it.isListening) it.stop() }
        resetMicButton()
    }

    private fun resetMicButton() {
        activeDialogBinding?.btnMic?.text = getString(R.string.voice_input)
        activeDialogBinding?.voiceStatus?.text = ""
    }

    private fun applyVoiceText(rawText: String) {
        val dialogBinding = activeDialogBinding ?: return

        // Fix common mis-transcriptions of medical terms before splitting
        // into fields, so the correction applies wherever the term lands.
        val corrected = MedicalTermCorrector.correct(rawText)
        val parsed = VoiceParser.parse(corrected)

        if (parsed.bedNumber.isNotBlank()) dialogBinding.inputBed.setText(parsed.bedNumber)
        if (parsed.patientName.isNotBlank()) dialogBinding.inputName.setText(parsed.patientName)
        if (parsed.consultant.isNotBlank()) dialogBinding.inputConsultant.setText(parsed.consultant)

        if (parsed.details.isNotBlank()) {
            val existingDetails = dialogBinding.inputDetails.text.toString()
            val newDetails = if (existingDetails.isBlank()) parsed.details else "$existingDetails\n${parsed.details}"
            dialogBinding.inputDetails.setText(newDetails)
        }

        Toast.makeText(this, "Voice text filled — please review before saving", Toast.LENGTH_SHORT).show()
    }

    // ---------- PDF / Print ----------

    private fun exportPdf() {
        lifecycleScope.launch {
            val records = adapter.currentList
            if (records.isEmpty()) {
                Toast.makeText(this@MainActivity, "No records to export", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val file = PdfExporter.exportToPdf(this@MainActivity, records)
            val uri = PdfExporter.getUriForFile(this@MainActivity, file)
            Toast.makeText(this@MainActivity, "PDF saved: ${file.name}", Toast.LENGTH_LONG).show()
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(viewIntent)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Saved to Documents/exports — no PDF viewer app found to open it",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun printRecords() {
        lifecycleScope.launch {
            val records = adapter.currentList
            if (records.isEmpty()) {
                Toast.makeText(this@MainActivity, "No records to print", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val file = PdfExporter.exportToPdf(this@MainActivity, records)
            val printManager = getSystemService(PRINT_SERVICE) as PrintManager
            val printAdapter = PdfPrintAdapter(file)
            printManager.print("WardLog Records", printAdapter, PrintAttributes.Builder().build())
        }
    }
}
