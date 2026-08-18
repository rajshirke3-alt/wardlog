package com.wardlog.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single user-editable dictionary entry.
 *
 * For category = "TERM": [alias] is the misheard/misspelled form (e.g.
 * "colon oscopy") and [canonical] is the correct spelling (e.g.
 * "Colonoscopy"). Used both for exact-phrase correction and, if long enough,
 * for fuzzy correction.
 *
 * For category = "DOCTOR": [alias] mirrors [canonical] (kept simple — not
 * used for exact matching); [canonical] is the doctor's full name WITHOUT
 * the "Dr" prefix (e.g. "Yatin Sagwekar"). Whatever the recognizer hears
 * after "dr"/"doctor" gets fuzzy-matched against these names.
 */
@Entity(tableName = "dictionary_entries")
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "TERM" or "DOCTOR"
    val alias: String,
    val canonical: String
) {
    companion object {
        const val CATEGORY_TERM = "TERM"
        const val CATEGORY_DOCTOR = "DOCTOR"
    }
}
