package com.example.campusbuddy.ui.ocr

/**
 * Pure text-parsing object that extracts structured student ID data from raw OCR text.
 * Contains NO Android dependencies — safe to test on JVM.
 *
 * All parsing strategies are in order of reliability:
 * 1. Levenshtein fuzzy label matching (resilient to OCR misreads like "Nane:" → "Name:")
 * 2. Labeled regex patterns
 * 3. Format-based pattern matching (e.g., known registration number formats)
 * 4. Heuristic detection (capitalization patterns, keyword matching)
 */
object StudentIdParser {

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parse raw OCR text and extract student ID fields for the front side.
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

    // ═══════════════════════════════════════════════════════════════
    // LEVENSHTEIN FUZZY MATCHING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Compute Levenshtein edit distance between two strings.
     * The minimum number of single-character edits (insertions, deletions, substitutions)
     * needed to transform [s1] into [s2].
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val s1Upper = s1.uppercase().trimEnd(':').trimEnd()
        val s2Upper = s2.uppercase().trimEnd(':').trimEnd()
        val m = s1Upper.length
        val n = s2Upper.length

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (s1Upper[i - 1] == s2Upper[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,           // deletion
                    curr[j - 1] + 1,       // insertion
                    prev[j - 1] + cost     // substitution
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }

    /**
     * Find a line that fuzzy-matches any of the given [labels] within [maxDistance].
     * Returns the text after the label separator (:, =, etc.) or the whole line if no separator.
     */
    fun fuzzyFindLabel(
        lines: List<String>,
        labels: List<String>,
        maxDistance: Int = 2
    ): String? {
        for (line in lines) {
            val cleaned = line.replace(Regex("^[•\\-*▶>·◦▪▸❯✦✧]\\s*"), "").trim()
            if (cleaned.isBlank()) continue

            for (label in labels) {
                // Check the beginning of the line against the label
                val linePrefix = cleaned.take(label.length + 4)
                val dist = levenshteinDistance(linePrefix, label)
                if (dist <= maxDistance) {
                    val value = cleaned.removePrefix(linePrefix)
                        .trimStart(':', ' ', '=', '-')
                        .trim()
                    if (value.isNotBlank()) return value
                }

                // Also check: does the line start close to this label?
                for (endIdx in label.length..minOf(label.length + 3, cleaned.length)) {
                    val prefix = cleaned.take(endIdx)
                    if (levenshteinDistance(prefix, label) <= maxDistance) {
                        val afterLabel = cleaned.drop(endIdx)
                            .trimStart(':', ' ', '=', '-', '.')
                            .trim()
                        if (afterLabel.isNotBlank()) return afterLabel
                    }
                }
            }
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════
    // FIELD EXTRACTION METHODS
    // ═══════════════════════════════════════════════════════════════

    fun extractFullName(lines: List<String>, fullText: String): String {
        // Strategy 1: Fuzzy label matching
        val fuzzyResult = fuzzyFindLabel(lines, listOf("Name", "Student Name", "Full Name", "Candidate", "Holder"), maxDistance = 2)
        if (fuzzyResult != null) {
            val cleaned = cleanName(fuzzyResult)
            if (cleaned.isNotBlank()) return cleaned
        }

        // Strategy 2: Labeled patterns via regex
        for (pattern in NAME_LABEL_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues.lastOrNull()?.trim() ?: return@forEach
                if (value.isNotBlank() && value.length in 3..60 && value.count { it == ' ' } in 1..4) {
                    return cleanName(value)
                }
            }
        }

        // Strategy 3: Look for lines with proper capitalization
        for (line in lines) {
            val cleaned = line.replace(Regex("^[•\\-*▶>·◦▪▸❯✦✧]\\s*"), "").trim()
            if (cleaned.matches(Regex("^[A-Z][a-z]+(?:\\s+[A-Z][a-z']+){1,3}$")) &&
                cleaned.length in 5..50 && !isOrganizationText(cleaned)
            ) {
                return cleaned
            }
        }

        // Strategy 4: Named entity pattern — two or more capitalized words
        val namePattern = Regex("""^([A-Z][a-z]{2,}(?:\s+[A-Z][a-z]{2,}){1,3})$""", RegexOption.MULTILINE)
        for (match in namePattern.findAll(fullText)) {
            val candidate = match.value.trim()
            if (candidate.length in 4..50 && !isOrganizationText(candidate) && !isAddressText(candidate)) {
                return candidate
            }
        }

        // Strategy 5: UPPERCASE name patterns (common on ID cards)
        val allCapsPattern = Regex("^[A-Z][A-Z]+(?:\\s+[A-Z][A-Z]+){1,3}$")
        for (line in lines) {
            val cleaned = line.replace(Regex("^[•\\-*▶>·◦▪▸❯✦✧]\\s*"), "").trim()
            if (cleaned.matches(allCapsPattern) && cleaned.length in 5..40 &&
                !isOrganizationText(cleaned) && !isAddressText(cleaned)
            ) {
                return cleaned.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() } + word.drop(1).lowercase()
                }
            }
        }

        return ""
    }

    fun extractRegNumber(lines: List<String>, fullText: String): String {
        // Strategy 1: Fuzzy label matching
        val fuzzyResult = fuzzyFindLabel(
            lines,
            listOf("Reg", "Reg No", "Reg Number", "Registration", "Registration No",
                   "Roll", "Roll No", "Roll Number", "Enroll", "Enrollment", "Enrollment No",
                   "Matric", "Matriculation", "ID", "Student ID", "Code"),
            maxDistance = 2
        )
        if (fuzzyResult != null && fuzzyResult.length in 4..25) {
            return fuzzyResult.uppercase()
        }

        // Strategy 2: Labeled patterns via regex
        for (pattern in REG_LABEL_PATTERNS) {
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues.last().trim()
                if (value.length in 5..25) return value.uppercase()
            }
        }

        // Strategy 3: Known registration number formats
        for (pattern in REG_FORMAT_PATTERNS) {
            val matches = pattern.findAll(fullText).toList()
            if (matches.isNotEmpty()) {
                val best = matches.maxByOrNull { it.value.length }?.value?.trim()?.uppercase() ?: ""
                if (best.any { it.isLetter() } && !isAddressText(best) && !isOrganizationText(best)) {
                    return best
                }
            }
        }

        // Strategy 4: Isolated alphanumeric sequences
        val tokenPattern = Regex("""\b([A-Za-z0-9]{6,15})\b""")
        val tokens = tokenPattern.findAll(fullText)
            .map { it.value.trim() }
            .filter { token ->
                token.any { it.isDigit() } && token.any { it.isLetter() } &&
                !token.any { it in "!@#\$%^&*()_+={}\\[\\]:;<>,.?/~\""} &&
                !token.contains("COLLEGE", ignoreCase = true) &&
                !token.contains("UNIVERSITY", ignoreCase = true) &&
                !token.contains("DEPARTMENT", ignoreCase = true) &&
                !token.contains("NAME", ignoreCase = true)
            }
            .toList()

        val bestToken = tokens.maxByOrNull { it.length }?.uppercase() ?: ""
        if (bestToken.isNotBlank() && !isAddressText(bestToken) && !isOrganizationText(bestToken)) {
            return bestToken
        }

        return ""
    }

    fun extractCollegeName(lines: List<String>, fullText: String): String {
        // Strategy 1: Fuzzy label matching
        val fuzzyResult = fuzzyFindLabel(
            lines,
            listOf("College", "Institute", "University", "School", "Institution", "Academy", "Campus"),
            maxDistance = 2
        )
        if (fuzzyResult != null && fuzzyResult.length in 3..80 && !isDepartmentLabel(fuzzyResult) && !isAddressText(fuzzyResult)) {
            return cleanCollegeName(fuzzyResult)
        }

        // Strategy 2: Regex patterns
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

        // Strategy 3: Look for lines with institution keywords
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

    fun extractDepartment(lines: List<String>, fullText: String): String {
        // Strategy 1: Fuzzy label matching
        val fuzzyResult = fuzzyFindLabel(
            lines,
            listOf("Dept", "Department", "Branch", "Programme", "Program",
                   "Course", "Major", "Stream", "Discipline", "Specialization"),
            maxDistance = 2
        )
        if (fuzzyResult != null && fuzzyResult.length in 2..50 &&
            !isAddressText(fuzzyResult) && !isOrganizationText(fuzzyResult)
        ) {
            return cleanDepartment(fuzzyResult)
        }

        // Strategy 2: Regex patterns
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

    fun extractYear(lines: List<String>, fullText: String): String {
        // Strategy 1: Fuzzy label matching
        val fuzzyResult = fuzzyFindLabel(
            lines,
            listOf("Year", "Batch", "Class", "Sem", "Semester", "Year of Study", "Academic Year"),
            maxDistance = 2
        )
        if (fuzzyResult != null) {
            val normalized = normalizeYear(fuzzyResult)
            if (normalized.isNotBlank()) return normalized
        }

        // Strategy 2: Regex patterns
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

    fun extractEmail(rawText: String): String {
        val emailPattern = Regex(
            """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.(?:ac\.[a-z]{2,}|edu(?:\.[a-z]{2,})?|in|org|com|net|edu\.in)""",
            RegexOption.IGNORE_CASE
        )
        val match = emailPattern.find(rawText)
        val email = match?.value?.lowercase() ?: ""

        if (email.isNotBlank()) {
            val domain = email.substringAfter("@")
            if (domain.contains("rediffmail") || domain.contains("protonmail")) {
                return ""
            }
        }

        return email
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════════════════

    fun isOrganizationText(text: String): Boolean {
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

    fun isAddressText(text: String): Boolean {
        if (text.contains(Regex("\\b\\d{6}\\b"))) return true
        val upper = text.uppercase()
        val addrKeywords = listOf(
            "ROAD", "STREET", "NAGAR", "COLONY", "LAYOUT", "CROSS", "MAIN", "LANE",
            "TILAKWADI", "SHAHAPUR", "PEENYA", "WHITEFIELD", "MARATHAHALLI",
            "BANASHANKARI", "JAYANAGAR", "INDIRANAGAR", "KORAMANGALA",
            "BTM", "HSR", "ELECTRONIC CITY",
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

    fun isDepartmentLabel(text: String): Boolean {
        val upper = text.uppercase()
        val deptKeywords = listOf(
            "DEPARTMENT", "DEPT", "BRANCH", "PROGRAMME", "PROGRAM",
            "COURSE", "MAJOR", "STREAM", "DIVISION", "SECTION"
        )
        if (deptKeywords.any { upper.contains(it) }) return true

        val degreeAbbr = Regex(
            """\b(BCA|BBA|BCOM|BSC|B TECH|BE|MBA|MCA|MSC|M TECH|BA|MA|BFA|LLB|LLM|B ARCH|M ARCH|B PHARM|M PHARM|BDS|MBBS|BHM|BHA|B ED|M ED|B LIB|M LIB|B PED|M PED|B\.VOC|M\.VOC|DIPLOMA|PGDCA|PGDM|PhD)\b""",
            RegexOption.IGNORE_CASE
        )
        return degreeAbbr.containsMatchIn(text)
    }

    // ═══════════════════════════════════════════════════════════════
    // CLEANING & NORMALIZATION
    // ═══════════════════════════════════════════════════════════════

    fun cleanName(name: String): String {
        return name
            .replace(Regex("[\\d_\"'`´‘’\"“”]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.length >= 2 || it.uppercase() == it }
            .joinToString(" ")
    }

    fun cleanCollegeName(name: String): String {
        return name
            .replace(Regex("[\\[\\]()\"'`´‘’\"“”]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
    }

    fun cleanDepartment(dept: String): String {
        return dept
            .replace(Regex("[\\[\\]()\"'`´‘’\"“”]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(50)
    }

    fun normalizeYear(raw: String): String {
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

    // ═══════════════════════════════════════════════════════════════
    // PATTERN CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    val NAME_LABEL_PATTERNS = listOf(
        Regex("""(?:Name|Student\s*Name|Full\s*Name|Candidate|Holder)[:\s]*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:Name|Student\s*Name)[:\s]*(.+)""", RegexOption.IGNORE_CASE),
    )

    val REG_LABEL_PATTERNS = listOf(
        Regex("""(?:Reg(?:istration)?\.?\s*(?:No|Number|#|Code|ID)?[=:\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
        Regex("""(?:Roll\s*(?:No|Number)?[=:\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
        Regex("""(?:Enroll(?:ment)?\.?\s*(?:No|Number)?[=:\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
        Regex("""(?:Matric(?:ulation)?\.?\s*(?:No|Number)?[=:\s]*)([A-Za-z0-9\-/]{5,25})""", RegexOption.IGNORE_CASE),
    )

    val REG_FORMAT_PATTERNS = listOf(
        Regex("""\b([A-Za-z]{2,4}\d{3,6}[A-Za-z]?\d*)\b"""),
        Regex("""\b(\d{2}[A-Za-z]{2}\d{3,6})\b"""),
        Regex("""\b(\d{4}[A-Za-z]{2,4}\d{3,8})\b"""),
        Regex("""\b(\d{4}/[A-Za-z]{2,6}/\d{2,6})\b"""),
        Regex("""\b(\d{2}/[A-Za-z]{2,6}/\d{2,6})\b"""),
        Regex("""\b([A-Za-z]{2,6}[-/]\d{2,4}[-/]\d{2,6})\b"""),
        Regex("""\b([A-Za-z]+\d{3,6}[A-Za-z]?)\b"""),
        Regex("""\b([A-Za-z]{2,8}-\d{3,8})\b"""),
        Regex("""\b(\d{8,12})\b"""),
    )

    val COLLEGE_PATTERNS = listOf(
        Regex("""(?:College|Institute|University|School|Institution|Academy)[:\s]*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:College|Institute|University|School)[:\s]*(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Z][A-Za-z\s]{4,}(?:College|Institute|University|School|Academy))\b"""),
    )

    val DEPARTMENT_PATTERNS = listOf(
        Regex("""(?:Dept|Department|Branch|Programme|Program|Course|Major|Stream)[:\s]*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:Dept|Department|Branch|Programme|Program)[:\s]*(.+)""", RegexOption.IGNORE_CASE),
        Regex("""\b(Computer\s*Science|Engineering|Business|Arts?|Commerce|Science|Mathematics|Physics|Chemistry|Biology|Medicine|Law|Management|Information\s*Technology)\b""", RegexOption.IGNORE_CASE),
    )

    val YEAR_PATTERNS = listOf(
        Regex("""(?:Year|Batch|Class|Sem(?:ester)?|Year of Study)[:\s]*(1st|2nd|3rd|4th|I{1,3}|I{0,3}V|1|2|3|4|20\d{2})""", RegexOption.IGNORE_CASE),
        Regex("""\b(20\d{2})\s*[–-]\s*(20\d{2})\b"""),
        Regex("""\b(1st|2nd|3rd|4th)\s*(?:year|yr|sem|semester)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(1st|2nd|3rd|4th)\b""", RegexOption.IGNORE_CASE),
    )
}
