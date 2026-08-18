package com.wardlog.app

/**
 * Lightweight, fully offline keyword-based extractor.
 *
 * Order independence: cue words ("bed", "patient", "consultant"/"doctor"/"dr")
 * are located wherever they occur in the sentence (word-boundary matched, so
 * "dr" won't false-match inside "address"), then sorted by position — so it
 * doesn't matter whether the speaker says patient name before bed number or
 * vice versa, each field is filled from the text that follows its own cue.
 *
 * "details" override: if the speaker says "details" (or "notes"), everything
 * from that word onward is treated as one Details blob and is NOT re-scanned
 * for bed/patient/consultant cues — so once someone starts free-form dictation
 * after saying "details", nothing after that point gets misfiled into the
 * structured columns.
 *
 * This is a heuristic, not a language model — it works best with reasonably
 * structured phrasing, e.g.:
 *   "Patient Ramesh Kumar, bed 12, consultant Dr Sharma, details on IV fluids
 *    since morning"
 */
object VoiceParser {

    data class ParsedRecord(
        val bedNumber: String = "",
        val patientName: String = "",
        val consultant: String = "",
        val details: String = ""
    )

    private val bedKeywords = listOf("bed number", "bed no", "bed", "cot number", "cot")
    private val nameKeywords = listOf("patient name", "patient is", "patient", "name of patient")
    private val consultantKeywords = listOf(
        "primary consultant", "consultant is", "consultant",
        "under doctor", "under dr", "doctor is", "doctor", "dr"
    )
    private val detailsKeywords = listOf("details", "detail", "notes", "note")

    private data class Hit(val field: String, val start: Int, val valueStart: Int)

    fun parse(rawText: String): ParsedRecord {
        val text = rawText.trim()
        if (text.isEmpty()) return ParsedRecord()

        // Find the earliest "details" trigger, if any. Everything before it
        // is fair game for bed/name/consultant extraction; everything from
        // it onward is captured verbatim as Details and not re-parsed.
        var detailsTriggerStart = -1
        var detailsValueStart = -1
        for (kw in detailsKeywords) {
            val regex = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            if (match != null && (detailsTriggerStart == -1 || match.range.first < detailsTriggerStart)) {
                detailsTriggerStart = match.range.first
                detailsValueStart = match.range.last + 1
            }
        }

        val searchableText = if (detailsTriggerStart != -1) text.substring(0, detailsTriggerStart) else text

        val hits = mutableListOf<Hit>()

        fun findHit(keywords: List<String>, field: String) {
            for (kw in keywords) {
                val regex = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE)
                val match = regex.find(searchableText)
                if (match != null) {
                    hits.add(Hit(field, match.range.first, match.range.last + 1))
                    break
                }
            }
        }

        findHit(bedKeywords, "bed")
        findHit(nameKeywords, "name")
        findHit(consultantKeywords, "consultant")

        val fields = mutableMapOf<String, String>()
        var leadingText: String

        if (hits.isNotEmpty()) {
            hits.sortBy { it.start }
            for (i in hits.indices) {
                val current = hits[i]
                val nextStart = if (i + 1 < hits.size) hits[i + 1].start else searchableText.length
                var value = searchableText.substring(current.valueStart, nextStart).trim()
                value = value.trimStart(':', '-', ',', ' ')
                if (value.startsWith("is ", ignoreCase = true)) value = value.substring(3)
                val stopIdx = value.indexOfFirst { it == '.' || it == ',' }
                val cleanValue = if (stopIdx != -1) value.substring(0, stopIdx).trim() else value.trim()
                fields[current.field] = cleanValue
            }
            leadingText = searchableText.substring(0, hits.first().start).trim().trimEnd(',', '.', ' ')
        } else {
            leadingText = searchableText.trim()
        }

        // Anything explicitly after a "details"/"notes" trigger word is
        // appended verbatim, untouched by further keyword parsing.
        val explicitDetails = if (detailsTriggerStart != -1) {
            text.substring(detailsValueStart).trim().trimStart(':', '-', ',', ' ')
        } else {
            ""
        }

        val combinedDetails = listOf(leadingText, explicitDetails)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        // If the cue word itself was "dr"/"doctor" (rather than "consultant"),
        // that word gets consumed during extraction above and the "Dr" prefix
        // is lost from the value — add it back so the column always reads
        // "Dr <Name>" regardless of which cue word was used.
        val consultantRaw = fields["consultant"]?.trim() ?: ""
        val consultantValue = when {
            consultantRaw.isEmpty() -> ""
            consultantRaw.startsWith("dr", ignoreCase = true) -> consultantRaw
            consultantRaw.startsWith("doctor", ignoreCase = true) -> consultantRaw
            else -> "Dr $consultantRaw"
        }

        return ParsedRecord(
            bedNumber = fields["bed"] ?: "",
            patientName = fields["name"] ?: "",
            consultant = consultantValue,
            details = combinedDetails
        )
    }
}
