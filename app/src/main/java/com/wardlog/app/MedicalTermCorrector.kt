package com.wardlog.app

import kotlin.math.max
import kotlin.math.min

/**
 * Fixes common speech-to-text errors for medical terms Android's recognizer
 * often mis-hears or mis-splits (abbreviations spoken as letters, unusual
 * medical words). Runs as a post-processing pass on the raw transcript
 * BEFORE VoiceParser splits it into fields, so corrections apply no matter
 * which field the term ends up in.
 *
 * Two layers:
 *  1. A curated alias list of known misheard variants for terms we expect
 *     to come up often on a ward (spelled-out abbreviations, procedure names).
 *  2. A fuzzy (edit-distance) fallback that catches near-misses on longer
 *     medical words the curated list didn't anticipate.
 *
 * Add more entries via the in-app Dictionary screen any time you notice a
 * term isn't being picked up correctly — no rebuild needed. DEFAULT_ALIASES
 * below is only the built-in seed list used the very first time the app
 * runs.
 */
object MedicalTermCorrector {

    // recognized phrase (lowercase, spaces only) -> canonical display text.
    // This is the built-in seed list only — used to populate the dictionary
    // database the very first time the app runs. After that, all correction
    // reads from `aliasCache` below, which is loaded from the database and
    // reflects whatever the user has added/edited/removed via the in-app
    // Dictionary screen.
    val DEFAULT_ALIASES: Map<String, String> = buildMap {
        put("ogd scopy", "OGD scopy")
        put("o g d scopy", "OGD scopy")
        put("ogd scope", "OGD scopy")
        put("old g d scopy", "OGD scopy")
        put("ogdoscopy", "OGD scopy")
        put("o g d oscopy", "OGD scopy")

        put("colonoscopy", "Colonoscopy")
        put("colon oscopy", "Colonoscopy")
        put("colonoscopy scan", "Colonoscopy")

        put("mrcp", "MRCP")
        put("m r c p", "MRCP")
        put("em ar see pea", "MRCP")
        put("em r c p", "MRCP")

        put("ercp", "ERCP")
        put("e r c p", "ERCP")
        put("e r cp", "ERCP")

        put("eus guided", "EUS guided")
        put("e u s guided", "EUS guided")
        put("use guided", "EUS guided")
        put("eos guided", "EUS guided")
        put("e us guided", "EUS guided")

        put("cect abdomen", "CECT Abdomen")
        put("c e c t abdomen", "CECT Abdomen")
        put("sekt abdomen", "CECT Abdomen")
        put("cec t abdomen", "CECT Abdomen")

        put("ct abdomen", "CT Abdomen")
        put("c t abdomen", "CT Abdomen")
        put("see tea abdomen", "CT Abdomen")
        put("cat abdomen", "CT Abdomen")

        put("lap", "LAP")

        put("laproscopic", "Laparoscopic")
        put("laparoscopic", "Laparoscopic")
        put("lap heroscopic", "Laparoscopic")
        put("lap roscopic", "Laparoscopic")
        put("laproscopy", "Laparoscopic")

        put("hemorrhoidectomy", "Hemorrhoidectomy")
        put("hemorrhoid ectomy", "Hemorrhoidectomy")
        put("hemroidectomy", "Hemorrhoidectomy")
        put("hemorrhoidectomy surgery", "Hemorrhoidectomy")

        put("fissurectomy", "Fissurectomy")
        put("fissure ectomy", "Fissurectomy")
        put("physiorectomy", "Fissurectomy")

        put("coloprep", "COLOprep")
        put("colo prep", "COLOprep")
        put("colon prep", "COLOprep")

        put("pac", "PAC")
        put("pack", "PAC")

        put("cardiac", "Cardiac")

        put("endocrinologist", "Endocrinologist")
        put("endocrine ologist", "Endocrinologist")
        put("endocrinology list", "Endocrinologist")

        put("surgeon", "Surgeon")
        put("sturgeon", "Surgeon")

        put("abdominal pain", "Abdominal pain")
        put("abdomen pain", "Abdominal pain")

        put("constipation", "Constipation")

        put("motion", "Motion")
        put("motions", "Motion")

        put("flatus", "Flatus")
        put("flat us", "Flatus")
        put("flattest", "Flatus")
        put("flat as", "Flatus")

        put("loose stools", "Loose stools")
        put("loose stool", "Loose stools")
        put("lose tools", "Loose stools")
        put("loose tools", "Loose stools")
        put("loose stole", "Loose stools")

        put("fever", "Fever")

        put("mri", "MRI")
        put("m r i", "MRI")
        put("em ar eye", "MRI")
        put("em r i", "MRI")

        put("clearance not come", "Clearance not come")
        put("clearance not came", "Clearance not come")
        put("clearance hasn't come", "Clearance not come")
        put("clearance not received", "Clearance not come")

        put("acute", "Acute")
        put("chronic", "Chronic")

        put("liver failure", "Liver failure")
        put("secondary", "Secondary")

        put("hepatitis a", "Hepatitis A")
        put("hepatitis b", "Hepatitis B")
        put("hepatitis c", "Hepatitis C")
        put("hepatitis", "Hepatitis")

        put("deranged", "Deranged")
        put("de ranged", "Deranged")
        put("resolving", "Resolving")
        put("informed", "Informed")

        put("nbm", "NBM")
        put("n b m", "NBM")
        put("en be em", "NBM")
        put("n bee em", "NBM")

        put("ns", "NS")
        put("n s", "NS")
        put("en es", "NS")
        put("normal saline", "NS")

        put("rl", "RL")
        put("r l", "RL")
        put("are el", "RL")
        put("ringer lactate", "RL")
        put("ringers lactate", "RL")

        put("trace", "Trace")

        put("blood culture", "Blood culture")
        put("blood cultures", "Blood culture")

        put("reports", "Reports")
        put("report", "Reports")

        put("pancreatitis", "Pancreatitis")
        put("pancreatitus", "Pancreatitis")

        put("appendicitis", "Appendicitis")
        put("appendicitus", "Appendicitis")

        put("eus guided fnb", "EUS guided FNB")
        put("e u s guided fnb", "EUS guided FNB")
        put("use guided fnb", "EUS guided FNB")

        put("cholecystectomy", "Cholecystectomy")
        put("chole cystectomy", "Cholecystectomy")
        put("colecystectomy", "Cholecystectomy")

        put("cholecystitis", "Cholecystitis")
        put("chole cystitis", "Cholecystitis")
        put("colecystitis", "Cholecystitis")

        put("cholelithiasis", "Cholelithiasis")
        put("chole lithiasis", "Cholelithiasis")
        put("colelithiasis", "Cholelithiasis")

        put("liver", "Liver")
        put("gall bladder", "Gall bladder")
        put("gallbladder", "Gall bladder")
        put("gall blabber", "Gall bladder")

        put("intestine", "Intestine")
        put("intestines", "Intestine")

        put("caecal", "Caecal")
        put("cecal", "Caecal")
        put("seekal", "Caecal")

        put("anus", "Anus")

        put("oesophagus", "Oesophagus")
        put("esophagus", "Oesophagus")
        put("issofagus", "Oesophagus")
    }

    // Longer/rarer canonical words used for the fuzzy-match fallback pass by
    // default. User-added dictionary terms of similar length automatically
    // join this list too (see loadFromDatabase below) — short or everyday
    // words (fever, liver, anus, trace...) are deliberately excluded so the
    // fuzzy pass doesn't "correct" ordinary speech by mistake.
    private val BASE_FUZZY_TARGETS = listOf(
        "Colonoscopy", "Hemorrhoidectomy", "Fissurectomy", "Endocrinologist",
        "Laparoscopic", "Constipation", "Flatus", "Pancreatitis", "Appendicitis",
        "Cholecystectomy", "Cholecystitis", "Cholelithiasis", "Oesophagus",
        "Hepatitis"
    )

    // Multi-word canonical phrases eligible for the phrase-level fuzzy pass
    // (see correctPhrases below) — e.g. "Gall bladder" mis-heard as "gaul
    // bladder", or "Loose stools" as "lose tools" in a way the curated alias
    // list didn't anticipate verbatim. User-added multi-word dictionary
    // terms join this automatically too.
    private val BASE_PHRASE_TARGETS = listOf(
        "Gall bladder", "Loose stools", "Abdominal pain", "Blood culture",
        "Clearance not come", "Liver failure", "CECT Abdomen", "CT Abdomen",
        "EUS guided FNB", "EUS guided", "OGD scopy"
    )

    // Built-in seed list of consultant/doctor names — used only to populate
    // the dictionary database the first time the app runs (see
    // AppDatabase.seedDictionaryIfEmpty). After that, doctorCache below
    // (loaded from the database) is the source of truth, and reflects
    // anything added/edited/removed via the in-app Dictionary screen.
    val DEFAULT_DOCTORS = listOf(
        "Yatin Sagwekar",
        "Chaitanya",
        "Bharat",
        "Poonam",
        "Sonali Gautam",
        "Dipak Bhangale"
    )

    // ---- Live, database-backed state -----------------------------------
    // These start out equal to the built-in defaults so the app still works
    // correctly even before the database has finished loading on first
    // launch, then get replaced by loadFromDatabase() with whatever the
    // user's dictionary actually contains.
    @Volatile private var aliasCache: Map<String, String> = DEFAULT_ALIASES
    @Volatile private var doctorCache: List<String> = DEFAULT_DOCTORS
    @Volatile private var fuzzyTargetsCache: List<String> = BASE_FUZZY_TARGETS
    @Volatile private var phraseTargetsCache: List<String> = BASE_PHRASE_TARGETS

    /** Full "Dr Name" strings — also used to bias the speech recognizer itself. */
    val doctorBiasStrings: List<String> get() = doctorCache.map { "Dr $it" }

    /** All canonical terms — used to bias the speech recognizer itself. */
    val termBiasStrings: List<String> get() = (aliasCache.values.toSet() + fuzzyTargetsCache).toList()

    /**
     * Refreshes the in-memory correction cache from the dictionary database.
     * Call this once at app startup (after seeding) and again any time the
     * user returns from the Dictionary screen, so edits take effect
     * immediately without needing to restart the app.
     */
    suspend fun loadFromDatabase(dao: DictionaryDao) {
        val rows = dao.getAllSync()
        val termRows = rows.filter { it.category == DictionaryEntry.CATEGORY_TERM }
        val doctorRows = rows.filter { it.category == DictionaryEntry.CATEGORY_DOCTOR }

        aliasCache = if (termRows.isNotEmpty()) {
            termRows.associate { it.alias.lowercase() to it.canonical }
        } else {
            DEFAULT_ALIASES
        }
        doctorCache = if (doctorRows.isNotEmpty()) {
            doctorRows.map { it.canonical }
        } else {
            DEFAULT_DOCTORS
        }
        fuzzyTargetsCache = (BASE_FUZZY_TARGETS + termRows.map { it.canonical }.filter { it.length >= 7 }).distinct()
        phraseTargetsCache = (
            BASE_PHRASE_TARGETS + termRows.map { it.canonical }.filter { it.contains(" ") && it.length >= 8 }
            ).distinct()
    }

    fun correct(rawText: String): String {
        if (rawText.isBlank()) return rawText

        var text = rawText

        // Pass 1: exact/known-alias phrase replacement. Longest aliases
        // first so multi-word aliases aren't broken up by single-word ones.
        val sortedAliases = aliasCache.entries.sortedByDescending { it.key.length }
        for ((alias, canonical) in sortedAliases) {
            val regex = Regex("\\b${Regex.escape(alias)}\\b", RegexOption.IGNORE_CASE)
            text = regex.replace(text, canonical)
        }

        // Pass 2: phrase-level fuzzy correction. Slides a window the same
        // number of words as each known multi-word phrase across the text
        // and snaps it to the canonical phrase if it's a close match —
        // catches mishearings the exact alias list didn't cover verbatim
        // (single-word fuzzy matching below can't fix these, since no
        // individual word is a close match to a multi-word phrase).
        text = correctPhrases(text)

        // Pass 3: fuzzy correction for individual words close to a known
        // tricky medical term, catching typos/mishearings the alias list
        // didn't cover verbatim.
        val words = text.split(Regex("\\s+"))
        val corrected = words.map { word ->
            val cleaned = word.trim('.', ',', ';', ':')
            if (cleaned.length < 5) return@map word // skip short/common words
            val bestMatch = fuzzyTargetsCache.minByOrNull { target ->
                levenshtein(cleaned.lowercase(), target.lowercase())
            }
            if (bestMatch != null) {
                val distance = levenshtein(cleaned.lowercase(), bestMatch.lowercase())
                val threshold = max(1, bestMatch.length / 4)
                if (distance <= threshold && !cleaned.equals(bestMatch, ignoreCase = true)) {
                    return@map word.replace(cleaned, bestMatch)
                }
            }
            word
        }

        // Pass 4: doctor name correction. Find "dr"/"doctor" followed by up
        // to 3 words, and if those words are a close spelling/phonetic match
        // for a known consultant, snap the whole thing to "Dr <Full Name>".
        return correctDoctorNames(corrected.joinToString(" "))
    }

    private fun correctPhrases(input: String): String {
        var text = input
        for (phrase in phraseTargetsCache) {
            val phraseWords = phrase.split(" ")
            val wordCount = phraseWords.size
            if (wordCount < 2) continue

            val words = text.split(Regex("\\s+"))
            if (words.size < wordCount) continue

            var bestIdx = -1
            var bestScore = 0.0
            var alreadyCorrect = false

            for (i in 0..words.size - wordCount) {
                val window = words.subList(i, i + wordCount).joinToString(" ")
                if (window.equals(phrase, ignoreCase = true)) {
                    alreadyCorrect = true
                    break
                }
                val a = window.lowercase().replace(Regex("[^a-z]"), "")
                val b = phrase.lowercase().replace(Regex("[^a-z]"), "")
                if (a.isEmpty() || b.isEmpty()) continue
                val distance = levenshtein(a, b)
                val maxLen = max(a.length, b.length)
                val score = 1.0 - (distance.toDouble() / maxLen)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }

            if (!alreadyCorrect && bestIdx != -1 && bestScore >= 0.72) {
                val mutableWords = text.split(Regex("\\s+"))
                val before = mutableWords.subList(0, bestIdx)
                val after = mutableWords.subList(bestIdx + wordCount, mutableWords.size)
                text = (before + phraseWords + after).joinToString(" ")
            }
        }
        return text
    }

    /**
     * Given several candidate transcriptions the recognizer considered
     * (ranked by its own acoustic confidence), re-ranks them using ward
     * vocabulary: whichever hypothesis lines up best with known terms and
     * doctor names wins, even if it wasn't the recognizer's own top guess —
     * acoustic models have no idea "OGD scopy" is a real phrase, so the
     * "obvious-sounding" English guess sometimes beats the correct one on
     * confidence alone. Falls back to the recognizer's top choice if no
     * candidate scores better than it.
     */
    fun pickBestHypothesis(candidates: List<String>): String {
        val nonBlank = candidates.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return ""
        if (nonBlank.size == 1) return nonBlank[0]

        var best = nonBlank[0]
        var bestScore = vocabularyScore(nonBlank[0])
        for (i in 1 until nonBlank.size) {
            val score = vocabularyScore(nonBlank[i])
            if (score > bestScore) {
                bestScore = score
                best = nonBlank[i]
            }
        }
        return best
    }

    private fun vocabularyScore(text: String): Int {
        val lower = text.lowercase()
        var score = 0
        for (alias in aliasCache.keys) {
            if (lower.contains(alias)) score += alias.length
        }
        for (doctor in doctorCache) {
            if (lower.contains(doctor.lowercase())) score += doctor.length
        }
        return score
    }

    private fun correctDoctorNames(text: String): String {
        val regex = Regex("""\b(dr\.?|doctor)\s+([a-zA-Z]+(?:\s+[a-zA-Z]+){0,2})""", RegexOption.IGNORE_CASE)
        return regex.replace(text) { match ->
            val candidateWords = match.groupValues[2].trim().split(Regex("\\s+"))
            var bestName: String? = null
            var bestScore = 0.0

            for (name in doctorCache) {
                val nameWordCount = name.split(" ").size
                if (candidateWords.size < nameWordCount) continue
                val window = candidateWords.take(nameWordCount).joinToString("")
                val target = name.replace(" ", "")
                val distance = levenshtein(window.lowercase(), target.lowercase())
                val maxLen = max(window.length, target.length)
                val score = if (maxLen == 0) 0.0 else 1.0 - (distance.toDouble() / maxLen)
                if (score > bestScore) {
                    bestScore = score
                    bestName = name
                }
            }

            // Threshold is deliberately forgiving (0.55) since speech
            // recognizers mangle proper names badly — but it still requires
            // most of the sounds to line up, so it won't misfire on an
            // unrelated word that happens to follow "dr"/"doctor".
            if (bestName != null && bestScore >= 0.55) "Dr $bestName" else match.value
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
