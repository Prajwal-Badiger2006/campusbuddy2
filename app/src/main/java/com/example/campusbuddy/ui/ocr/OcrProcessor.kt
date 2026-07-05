package com.example.campusbuddy.ui.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class OcrProcessor {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Process a bitmap through ML Kit and return the raw recognized text.
     * Automatically pre-processes the image for better OCR accuracy.
     *
     * @param bitmap The original captured bitmap
     * @param useFullPreprocessing Whether to apply full (slower) or quick (faster) pre-processing
     * @return The recognized text string
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        useFullPreprocessing: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        // ML Kit is trained on natural images — start with lighter pre-processing
        // that preserves image gradients and textures the model uses for recognition.
        // Only fall back to aggressive binarization if the first pass fails.

        // Step 1: Light pre-processing — grayscale + CLAHE contrast enhancement
        // This removes color noise while preserving the gradient information ML Kit needs.
        val lightBitmap = ImagePreprocessor.quickPreprocess(bitmap)
        val lightResult = runRecognition(lightBitmap, rotationDegrees)

        // Step 2: If light pre-processing yields text, return immediately
        if (lightResult.isNotBlank()) {
            return@withContext lightResult
        }

        // Step 3: Fall back to aggressive binarization for difficult cases
        // (reflective surfaces, low contrast, poor lighting)
        if (useFullPreprocessing) {
            val fullBitmap = ImagePreprocessor.preprocessForOcr(bitmap)
            val fullResult = runRecognition(fullBitmap, rotationDegrees)
            if (fullResult.isNotBlank()) {
                return@withContext fullResult
            }
        }

        // Step 4: Last resort — run on the original bitmap without any processing
        val rawResult = runRecognition(bitmap, rotationDegrees)
        rawResult
    }

    /**
     * Run ML Kit text recognition on a pre-processed bitmap.
     */
    private suspend fun runRecognition(bitmap: Bitmap, rotationDegrees: Int): String {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Use the full text output
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        }
    }

    /**
     * Parse raw OCR text and extract student ID fields for the front side.
     * Front side typically contains: Name, Registration Number, College Name.
     *
     * Uses multi-strategy extraction:
     * 1. Labeled field matching (e.g., "Name: John Doe")
     * 2. Pattern-based extraction (e.g., registration number formats)
     * 3. Positional heuristics (field order on card)
     * 4. Fuzzy fallback for partial matches
     */
    fun parseFrontSide(rawText: String): StudentIdData {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fullText = rawText.trim()

        return StudentIdData(
            fullName = extractFullName(lines, fullText),
            registrationNumber = extractRegNumber(lines, fullText),
            collegeName = extractCollegeName(lines, fullText),
            department = extractDepartment(lines, fullText),
            year = extractYear(lines, fullText),
            frontScanned = true
        )
    }

    /**
     * Parse raw OCR text and extract student ID fields for the back side.
     * Back side typically contains: Department, Year, Email Address.
     */
    fun parseBackSide(rawText: String, existingData: StudentIdData): StudentIdData {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fullText = rawText.trim()

        return existingData.copy(
            department = if (existingData.department.isBlank())
                extractDepartment(lines, fullText)
            else existingData.department,
            year = if (existingData.year.isBlank())
                extractYear(lines, fullText)
            else existingData.year,
            email = if (existingData.email.isBlank())
                extractEmail(rawText)
            else existingData.email,
            backScanned = true
        )
    }

    /**
     * Full parse: run both front and back parsers and merge results.
     */
    fun parseFull(rawText: String): StudentIdData {
        val front = parseFrontSide(rawText)
        return parseBackSide(rawText, front)
    }

    // ═══════════════════════════════════════════
    // FIELD EXTRACTION METHODS
    // ═══════════════════════════════════════════

    /**
     * Extract full name using multiple strategies:
     * 1. Labeled patterns (Name:, Student Name:, etc.)
     * 2. Capitalized name pattern
     * 3. First line heuristic
     */
    private fun extractFullName(lines: List<String>, fullText: String): String {
        // Strategy 1: Labeled field
        for (pattern in NAME_LABEL_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues.lastOrNull()?.trim() ?: return@forEach
                if (value.isNotBlank() && value.length in 3..60 && value.count { it == ' ' } in 1..4) {
                    return cleanName(value)
                }
            }
        }

        // Strategy 2: Look for lines with proper capitalization (first line likely name)
        for (line in lines) {
            val cleaned = line.replace(Regex("^[•\\-*▶>·◦▪▸❯✦✧]\\s*"), "").trim()
            // Name should have proper capitalization and be 2-4 words
            // Exclude org/trust/society names to avoid capturing them as person names
            if (cleaned.matches(Regex("^[A-Z][a-z]+(?:\\s+[A-Z][a-z']+){1,3}$")) &&
                cleaned.length in 5..50 && !isOrganizationText(cleaned)
            ) {
                return cleaned
            }
        }

        // Strategy 3: Named entity pattern — two or more capitalized words
        val namePattern = Regex("""^([A-Z][a-z]{2,}(?:\s+[A-Z][a-z]{2,}){1,3})$""", RegexOption.MULTILINE)
        for (match in namePattern.findAll(fullText)) {
            val candidate = match.value.trim()
            if (candidate.length in 4..50 && !isOrganizationText(candidate) && !isAddressText(candidate)) {
                return candidate
            }
        }

        // Strategy 4: UPPERCASE name patterns (common on ID cards)
        // Look for a line that is all-caps, 2-4 words, 5-40 chars — likely a person's name
        // Exclude lines with org keywords, PIN codes, or address markers
        val allCapsPattern = Regex("^[A-Z][A-Z]+(?:\\s+[A-Z][A-Z]+){1,3}$")
        for (line in lines) {
            val cleaned = line.replace(Regex("^[•\\-*▶>·◦▪▸❯✦✧]\\s*"), "").trim()
            if (cleaned.matches(allCapsPattern) &&
                cleaned.length in 5..40 &&
                !isOrganizationText(cleaned) && !isAddressText(cleaned)
            ) {
                // Convert to title case for cleaner display
                return cleaned.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } + word.drop(1).lowercase() }
            }
        }

        return ""
    }

    /**
     * Extract registration number using patterns common in Indian/global student IDs.
     * Formats supported:
     * - CS21B001, 2021CS001, 21CS001
     * - 12345678 (8-digit roll numbers)
     * - REG123456, REG-2021-001
     * - 2021CS210001F (long alphanumeric)
     * - 4-digit year + department code + serial (e.g., 2021BCA001, 2022CS001)
     * - Slash-separated university registration (e.g., 2021/BCA/001, 21/CS/01)
     */
    private fun extractRegNumber(lines: List<String>, fullText: String): String {
        // Strategy 1: Labeled field
        for (pattern in REG_LABEL_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues.last().trim()
                if (value.length in 5..25) return value.uppercase()
            }
        }

        // Strategy 2: Known registration number formats (most reliable)
        for (pattern in REG_FORMAT_PATTERNS) {
            val matches = pattern.findAll(fullText).toList()
            if (matches.isNotEmpty()) {
                // Take the longest match — likely the most complete
                val best = matches.maxByOrNull { it.value.length }?.value?.trim()?.uppercase() ?: ""
                // Exclude matches that are part of addresses (e.g., PIN codes) or org names
                if (best.any { it.isLetter() } && !isAddressText(best) && !isOrganizationText(best)) {
                    return best
                }
            }
        }

        // Strategy 3: Isolated alphanumeric sequences of appropriate length
        val tokenPattern = Regex("""\b([A-Za-z0-9]{6,15})\b""")
        val tokens = tokenPattern.findAll(fullText)
            .map { it.value.trim() }
            .filter { token ->
                token.any { it.isDigit() } &&
                token.any { it.isLetter() } &&
                !token.any { it in "!@#\$%^&*()_+={}\\[\\]:;<>,.?/~\""} &&
                !token.contains("COLLEGE", ignoreCase = true) &&
                !token.contains("UNIVERSITY", ignoreCase = true) &&
                !token.contains("DEPARTMENT", ignoreCase = true) &&
                !token.contains("NAME", ignoreCase = true)
            }
            .toList()

        // Return the longest alpha-numeric token that is most likely a registration number
        val bestToken = tokens.maxByOrNull { it.length }?.uppercase() ?: ""
        // Exclude address/organization matches
        if (bestToken.isNotBlank() && !isAddressText(bestToken) && !isOrganizationText(bestToken)) {
            return bestToken
        }

        return ""
    }

    /**
     * Extract college/institution name.
     * 
     * Uses labeled patterns (College:, Institute:, University:) and direct name matching.
     * Excludes department-only labels (e.g., "BCA DEPARTMENT", "Computer Science Department")
     * from being extracted as the college name.
     */
    private fun extractCollegeName(lines: List<String>, fullText: String): String {
        for (pattern in COLLEGE_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues.lastOrNull()?.trim() ?: return@forEach
                if (value.isNotBlank() && value.length in 3..80 &&
                    !isDepartmentLabel(value) && !isAddressText(value)
                ) {
                    return cleanCollegeName(value)
                }
            }
        }

        // Fallback: Look for a line that contains common institution keywords
        // but exclude lines that are just department names (e.g., "BCA DEPARTMENT")
        val institutionKeywords = Regex(
            """\b(COLLEGE|UNIVERSITY|INSTITUTE|SCHOOL|ACADEMY|EDUCATION|SOCIETY|TRUST)\b""",
            RegexOption.IGNORE_CASE
        )
        for (line in lines) {
            val cleaned = line.trim()
            if (cleaned.length in 5..80 && institutionKeywords.containsMatchIn(cleaned) &&
                !isDepartmentLabel(cleaned)
            ) {
                return cleanCollegeName(cleaned)
            }
        }

        return ""
    }

    /**
     * Extract department/branch/programme name.
     * 
     * Uses labeled patterns (Dept:, Department:, Branch:, etc.) and common department name matching.
     * Includes guards to exclude addresses (with PIN codes) and organization names
     * from being misidentified as the department.
     */
    private fun extractDepartment(lines: List<String>, fullText: String): String {
        for (pattern in DEPARTMENT_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues.lastOrNull()?.trim() ?: return@forEach
                if (value.isNotBlank() && value.length in 2..50 &&
                    !isAddressText(value) && !isOrganizationText(value)
                ) {
                    return cleanDepartment(value)
                }
            }
        }
        return ""
    }

    /**
     * Extract year of study.
     */
    private fun extractYear(lines: List<String>, fullText: String): String {
        for (pattern in YEAR_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues.last().trim()
                if (value.isNotBlank()) {
                    return normalizeYear(value)
                }
            }
        }
        return ""
    }

    /**
     * Extract email address from the scanned ID.
     * 
     * Matches common email patterns including:
     * - name@domain.com
     * - name@domain.ac.in
     * - name@institute.org
     */
    private fun extractEmail(rawText: String): String {
        val emailPattern = Regex(
            """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.(?:ac\.[a-z]{2,}|edu(?:\.[a-z]{2,})?|in|org|com|net|edu\.in)""",
            RegexOption.IGNORE_CASE
        )
        val match = emailPattern.find(rawText)
        val email = match?.value?.lowercase() ?: ""

        // Personal domains like gmail.com, yahoo.com are allowed since users
        // now sign up with a personal email. The isVerifiedStudent flag is set
        // through the ID scan, not the email domain.
        if (email.isNotBlank()) {
            val domain = email.substringAfter("@")
            // Reject purely personal/social domains as they are rarely used on student IDs
            if (domain.contains("rediffmail") || domain.contains("protonmail")
            ) {
                return ""
            }
        }

        return email
    }

    // ═══════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════

    /**
     * Check if text looks like an organization/trust/society/institution name
     * rather than a person's name. Used to prevent org names from being
     * extracted as the student's full name.
     */
    private fun isOrganizationText(text: String): Boolean {
        val upper = text.uppercase()
        val orgKeywords = listOf(
            "SOCIETY", "TRUST", "EDUCATIONAL", "EDUCATION", "INSTITUTE", "INSTITUTION",
            "FOUNDATION", "BOARD", "COUNCIL", "ASSOCIATION", "ORGANIZATION", "ORGANISATION",
            "COMMITTEE", "SYNDICATE", "ACADEMY", "UNIVERSITY", "COLLEGE", "SCHOOL",
            "CORPORATION", "AUTHORITY", "FEDERATION", "GUILD", "CONSORTIUM", "COMMISSION",
            "MISSION", "MINISTRY", "DIRECTORATE", "BUREAU", "CELL", "FORUM", "NETWORK"
        )
        return orgKeywords.any { upper.contains(it) }
    }

    /**
     * Check if text looks like an address (contains PIN code, city name,
     * or address keywords). Used to prevent addresses from being captured
     * as department, college name, or person name fields.
     */
    private fun isAddressText(text: String): Boolean {
        // Check for Indian PIN code (6 consecutive digits)
        if (text.contains(Regex("\\b\\d{6}\\b"))) return true
        val upper = text.uppercase()
        val addrKeywords = listOf(
            "ROAD", "STREET", "NAGAR", "COLONY", "LAYOUT", "CROSS", "MAIN", "LANE",
            "TILAKWADI", "SHAHAPUR", "PEENYA", "WHITEFIELD", "MARATHAHALLI",
            "BANASHANKARI", "JAYANAGAR", "INDIRANAGAR", "KORAMANGALA",
            "BTM", "HSR", "ELECTRONIC CITY",
            // Common Indian cities
            "BELAGAVI", "BELGAUM", "BANGALORE", "BENGALURU", "MUMBAI",
            "DELHI", "CHENNAI", "KOLKATA", "HYDERABAD", "PUNE", "AHMEDABAD",
            "SURAT", "JAIPUR", "LUCKNOW", "KANPUR", "NAGPUR", "INDORE",
            "THANE", "BHOPAL", "VISAKHAPATNAM", "PATNA", "VADODARA",
            "GHAZIABAD", "LUDHIANA", "AGRA", "NASHIK", "RANCHI", "MEERUT",
            "RAJKOT", "VARANASI", "SRINAGAR", "AURANGABAD", "DHANBAD",
            "AMRITSAR", "NAVI MUMBAI", "PRAYAGRAJ", "HOWRAH", "COIMBATORE",
            "JABALPUR", "GWALIOR", "VIJAYAWADA", "JODHPUR", "MADURAI",
            "RAIPUR", "KOTA", "CHANDIGARH", "GUWAHATI", "SOLAPUR",
            "HUBLI", "DHARWAD", "MYSORE", "MANGALORE", "MANGALURU"
        )
        return addrKeywords.any { upper.contains(it) }
    }

    /**
     * Check if text looks like a department label rather than a college name.
     * Prevents strings like "BCA DEPARTMENT", "Computer Science Department",
     * or "BCA" from being extracted as the college/institution name.
     */
    private fun isDepartmentLabel(text: String): Boolean {
        val upper = text.uppercase()
        val deptKeywords = listOf(
            "DEPARTMENT", "DEPT", "BRANCH", "PROGRAMME", "PROGRAM",
            "COURSE", "MAJOR", "STREAM", "DIVISION", "SECTION"
        )
        if (deptKeywords.any { upper.contains(it) }) return true

        // Check for common degree abbreviations that indicate a department name
        // (e.g., "BCA DEPARTMENT", "BBA", "BCOM", "MCA")
        val degreeAbbr = Regex(
            """\b(BCA|BBA|BCOM|BSC|B TECH|BE|MBA|MCA|MSC|M TECH|BA|MA|BFA|
            |LLB|LLM|B ARCH|M ARCH|B PHARM|M PHARM|BDS|MBBS|BHM|BHA|
            |B ED|M ED|B LIB|M LIB|B PED|M PED|B.VOC|M.VOC|
            |DIPLOMA|PGDCA|PGDM|PhD)\b""".trimMargin(),
            RegexOption.IGNORE_CASE
        )
        return degreeAbbr.containsMatchIn(text)
    }

    // ═══════════════════════════════════════════
    // CLEANING & NORMALIZATION
    // ═══════════════════════════════════════════

    private fun cleanName(name: String): String {
        return name
            .replace(Regex("[\\d_\"'`´‘’\"“”]"), "") // Remove digits and quotes
            .replace(Regex("\\s+"), " ")            // Collapse whitespace
            .trim()
            .split(" ")
            .filter { it.length >= 2 || it.uppercase() == it }
            .joinToString(" ")
    }

    private fun cleanCollegeName(name: String): String {
        return name
            .replace(Regex("[\\[\\]()\"'`´‘’\"“”]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
    }

    private fun cleanDepartment(dept: String): String {
        return dept
            .replace(Regex("[\\[\\]()\"'`´‘’\"“”]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(50)
    }

    private fun normalizeYear(raw: String): String {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return when {
            raw.matches(Regex("^20\\d{2}$")) -> {
                val startYear = raw.toInt()
                val diff = currentYear - startYear
                when {
                    diff <= 0 -> "1st"
                    diff == 1 -> "2nd"
                    diff == 2 -> "3rd"
                    else -> "4th"
                }
            }
            raw.equals("I", ignoreCase = true) || raw == "1" -> "1st"
            raw.equals("II", ignoreCase = true) || raw == "2" -> "2nd"
            raw.equals("III", ignoreCase = true) || raw == "3" -> "3rd"
            raw.equals("IV", ignoreCase = true) || raw == "4" -> "4th"
            raw.contains("1st", ignoreCase = true) -> "1st"
            raw.contains("2nd", ignoreCase = true) -> "2nd"
            raw.contains("3rd", ignoreCase = true) -> "3rd"
            raw.contains("4th", ignoreCase = true) -> "4th"
            else -> raw.trim()
        }
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        // ── Name extraction patterns ──
        private val NAME_LABEL_PATTERNS = listOf(
            Regex("""(?:Name|Student\s*Name|Full\s*Name|Candidate|Holder)[:\s]*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Name|Student\s*Name)[:\s]*(.+)""", RegexOption.IGNORE_CASE),
        )

        // ── Registration number patterns ──
        private val REG_LABEL_PATTERNS = listOf(
            Regex("""(?:Reg(?:istration)?\.?\s*(?:No|Number|#|Code|ID)?[:=\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
            Regex("""(?:Roll\s*(?:No|Number)?[:=\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
            Regex("""(?:Enroll(?:ment)?\.?\s*(?:No|Number)?[:=\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
            Regex("""(?:Matric(?:ulation)?\.?\s*(?:No|Number)?[:=\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
        )

        private val REG_FORMAT_PATTERNS = listOf(
            // Pattern: 2-4 letters + 2-4 digits + optional letters (e.g., CS21B001, 2021CS001)
            Regex("""\b([A-Za-z]{2,4}\d{3,6}[A-Za-z]?\d*)\b"""),
            // Pattern: 2 digits + 2 letters + 3-6 digits (e.g., 21CS001)
            Regex("""\b(\d{2}[A-Za-z]{2}\d{3,6})\b"""),
            // Pattern: 4 digits + 2-4 letters + 3-6 digits (e.g., 2021CS210001, 2021BCA001)
            Regex("""\b(\d{4}[A-Za-z]{2,4}\d{3,8})\b"""),
            // Pattern: 4-digit year + slash + department code + slash + serial (e.g., 2021/BCA/001)
            Regex("""\b(\d{4}/[A-Za-z]{2,6}/\d{2,6})\b"""),
            // Pattern: 2-digit year + slash + department + slash + serial (e.g., 21/CS/01)
            Regex("""\b(\d{2}/[A-Za-z]{2,6}/\d{2,6})\b"""),
            // Pattern: A dash-separated registration (e.g., REG-2021-001, 2021-CS-001)
            Regex("""\b([A-Za-z]{2,6}[-/]\d{2,4}[-/]\d{2,6})\b"""),
            // Pattern: Letters followed by digits with optional trailing letter (e.g., CS21001, STU2021)
            Regex("""\b([A-Za-z]+\d{3,6}[A-Za-z]?)\b"""),
            // Pattern: Letters + dash + digits (e.g., REG-001, STU-2021)
            Regex("""\b([A-Za-z]{2,8}-\d{3,8})\b"""),
            // Fallback: plain 8-12 digit number (exclude PIN codes which are 6 digits)
            Regex("""\b(\d{8,12})\b"""),
        )

        // ── College name patterns ──
        private val COLLEGE_PATTERNS = listOf(
            Regex("""(?:College|Institute|University|School|Institution|Academy)[:\s]*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:College|Institute|University|School)[:\s]*(.+)""", RegexOption.IGNORE_CASE),
            // Direct match for common institution names
            Regex("""\b([A-Z][A-Za-z\s]{4,}(?:College|Institute|University|School|Academy))\b"""),
        )

        // ── Department patterns ──
        private val DEPARTMENT_PATTERNS = listOf(
            Regex("""(?:Dept|Department|Branch|Programme|Program|Course|Major|Stream)[:\s]*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Dept|Department|Branch|Programme|Program)[:\s]*(.+)""", RegexOption.IGNORE_CASE),
            // Common department names
            Regex("""\b(Computer\s*Science|Engineering|Business|Arts?|Commerce|Science|Mathematics|Physics|Chemistry|Biology|Medicine|Law|Management|Information\s*Technology)\b""", RegexOption.IGNORE_CASE),
        )

        // ── Year patterns ──
        private val YEAR_PATTERNS = listOf(
            Regex("""(?:Year|Batch|Class|Sem(?:ester)?|Year of Study)[:\s]*(1st|2nd|3rd|4th|I{1,3}|I{0,3}V|1|2|3|4|20\d{2})""", RegexOption.IGNORE_CASE),
            Regex("""\b(20\d{2})\s*[-–]\s*(20\d{2})\b"""),
            Regex("""\b(1st|2nd|3rd|4th)\s*(?:year|yr|sem|semester)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(1st|2nd|3rd|4th)\b""", RegexOption.IGNORE_CASE),
        )
    }
}
