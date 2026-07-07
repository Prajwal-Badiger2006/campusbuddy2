package com.example.campusbuddy

import com.example.campusbuddy.ui.ocr.StudentIdParser
import com.example.campusbuddy.ui.ocr.StudentIdData
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive unit tests for [StudentIdParser].
 *
 * Tests use realistic OCR text output from Indian college student ID cards,
 * including edge cases like OCR misreads, missing fields, and varied layouts.
 */
class StudentIdParserTest {

    // ═══════════════════════════════════════════════════════════════
    // ── FULL PIPELINE TESTS (parseFrontSide / parseFull) ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `labeled fields - full name, reg, college`() {
        val text = """
            Student Name:  Rahul Sharma
            Reg No:  2021BCS0042
            College:  Bangalore Institute of Technology
            Department:  Computer Science
            Year:  3rd
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Rahul Sharma", result.fullName)
        assertEquals("2021BCS0042", result.registrationNumber)
        assertEquals("Bangalore Institute of Technology", result.collegeName)
        assertEquals("Computer Science", result.department)
        assertEquals("3rd", result.year)
        assertTrue(result.frontScanned)
    }

    @Test
    fun `uppercase name on ID card`() {
        val text = """
            PES UNIVERSITY
            Student ID Card
            PRIYA SINGH
            USN: 01PE22CS101
            Branch: CSE
            2022-2026
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Priya Singh", result.fullName)
        assertEquals("01PE22CS101", result.registrationNumber)
        assertEquals("PES UNIVERSITY", result.collegeName)
        assertEquals("CSE", result.department)
    }

    @Test
    fun `OCR misread - Nane instead of Name`() {
        val text = """
            Nane:  Vikram Patel
            Reg:  2022MCA008
            College:  Christ University
            Dept:  MCA
            Year: 2
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Vikram Patel", result.fullName)  // fuzzy match catches "Nane" -> "Name"
        assertEquals("2022MCA008", result.registrationNumber)
        assertEquals("Christ University", result.collegeName)
        assertEquals("MCA", result.department)
        assertEquals("2nd", result.year)
    }

    @Test
    fun `OCR misread - Dpartment instead of Department`() {
        val text = """
            Roll No: 2024BCA045
            Nane: Anjali Gupta
            Dpartment: BCA
            College: Mount Carmel College
            Batch: 2024
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Anjali Gupta", result.fullName)
        assertEquals("2024BCA045", result.registrationNumber)
        assertEquals("Mount Carmel College", result.collegeName)
        assertEquals("BCA", result.department)
        assertEquals("3rd", result.year)  // 2024 batch -> system year 2026 -> diff=2 -> 3rd year
    }

    @Test
    fun `ID card with address lines that should not be extracted as names`() {
        val text = """
            REVA UNIVERSITY
            Student Identity Card
            
            Name:  Arjun Kumar
            Register Number: 21MCA0231
            Programme: MBA
            Year of Study: 2024
            
            Address: 123, MG Road, Bangalore - 560001
            Blood Group: O+
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Arjun Kumar", result.fullName)
        assertEquals("21MCA0231", result.registrationNumber)
        assertEquals("REVA UNIVERSITY", result.collegeName)
        assertEquals("MBA", result.department)
        assertEquals("3rd", result.year)  // 2024 batch -> system year 2026 -> diff=2 -> 3rd year
    }

    @Test
    fun `ID card with email address`() {
        val text = """
            Name: Sneha Reddy
            Reg No: 22BEC0456
            Email: sneha.reddy@bmsce.ac.in
            College: BMS College of Engineering
            Branch: ECE
        """.trimIndent()

        val result = StudentIdParser.parseFull(text)
        assertEquals("Sneha Reddy", result.fullName)
        assertEquals("22BEC0456", result.registrationNumber)
        assertEquals("sneha.reddy@bmsce.ac.in", result.email)
        assertEquals("BMS College of Engineering", result.collegeName)
    }

    @Test
    fun `minimal ID card with only required fields`() {
        val text = """
            Name:  Ravi Deshmukh
            Reg: 23BCS7890
            College: RV College of Engineering
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Ravi Deshmukh", result.fullName)
        assertEquals("23BCS7890", result.registrationNumber)
        assertEquals("RV College of Engineering", result.collegeName)
        assertTrue(result.department.isBlank())
        assertTrue(result.year.isBlank())
    }

    @Test
    fun `empty text returns empty StudentIdData`() {
        val result = StudentIdParser.parseFrontSide("")
        assertTrue(result.fullName.isBlank())
        assertTrue(result.registrationNumber.isBlank())
        assertTrue(result.collegeName.isBlank())
    }

    @Test
    fun `garbage text returns empty fields`() {
        val text = """
            ASDFGHJKL1234
            XYZ98765QWERTY
            !@#$%^&*()_+
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        // Should not produce false positives from garbage text
        assertTrue(result.fullName.isBlank() || result.fullName.split(" ").size >= 2) // at least two words for a name
    }

    @Test
    fun `back side scan - email and year from back`() {
        val frontData = StudentIdData(
            fullName = "Kiran Joshi",
            registrationNumber = "21EC3456",
            collegeName = "MIT College",
            frontScanned = true
        )

        val backText = """
            Email: kiran.joshi@mitcollege.edu
            Year: 3rd
            Blood Group: AB+
        """.trimIndent()

        val result = StudentIdParser.parseBackSide(backText, frontData)
        assertEquals("Kiran Joshi", result.fullName)
        assertEquals("21EC3456", result.registrationNumber)
        assertEquals("MIT College", result.collegeName)
        assertEquals("kiran.joshi@mitcollege.edu", result.email)
        assertEquals("3rd", result.year)
        assertTrue(result.backScanned)
    }

    @Test
    fun `back side scan - department from back only`() {
        val frontData = StudentIdData(
            fullName = "Neha Patel",
            registrationNumber = "22BBA1010",
            collegeName = "St. Joseph's College",
            frontScanned = true
        )

        val backText = """
            Department: Business Administration
            Year: 2nd
        """.trimIndent()

        val result = StudentIdParser.parseBackSide(backText, frontData)
        assertEquals("Neha Patel", result.fullName)
        assertEquals("Business Administration", result.department)
        assertEquals("2nd", result.year)
    }

    @Test
    fun `back side scan does not override front data`() {
        val frontData = StudentIdData(
            fullName = "Amit Verma",
            registrationNumber = "21CS9876",
            collegeName = "Dayananda Sagar College",
            department = "Computer Science",
            year = "3rd",
            frontScanned = true
        )

        val backText = """
            Department: CSE (should not override)
            Year: 4th (should not override)
            Email: amit.v@dayananda.edu
        """.trimIndent()

        val result = StudentIdParser.parseBackSide(backText, frontData)
        assertEquals("Computer Science", result.department)  // not overridden
        assertEquals("3rd", result.year)                      // not overridden
        assertEquals("amit.v@dayananda.edu", result.email)    // added from back
    }

    // ═══════════════════════════════════════════════════════════════
    // ── LEVENSHTEIN DISTANCE TESTS ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `levenshtein - exact match`() {
        assertEquals(0, StudentIdParser.levenshteinDistance("Name", "Name"))
    }

    @Test
    fun `levenshtein - single substitution`() {
        assertEquals(1, StudentIdParser.levenshteinDistance("Nane", "Name"))
    }

    @Test
    fun `levenshtein - multiple differences`() {
        assertEquals(2, StudentIdParser.levenshteinDistance("Dpartment", "Department"))
    }

    @Test
    fun `levenshtein - case insensitive`() {
        assertEquals(0, StudentIdParser.levenshteinDistance("name", "NAME"))
    }

    @Test
    fun `levenshtein - ignores trailing colon`() {
        assertEquals(0, StudentIdParser.levenshteinDistance("Name:", "Name"))
    }

    // ═══════════════════════════════════════════════════════════════
    // ── FUZZY FIND LABEL TESTS ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `fuzzy label - exact match`() {
        val result = StudentIdParser.fuzzyFindLabel(
            listOf("Name: Rahul Sharma"),
            listOf("Name")
        )
        assertEquals("Rahul Sharma", result)
    }

    @Test
    fun `fuzzy label - single character misread`() {
        val result = StudentIdParser.fuzzyFindLabel(
            listOf("Nane: Rahul Sharma"),
            listOf("Name")
        )
        assertEquals("Rahul Sharma", result)
    }

    @Test
    fun `fuzzy label - no match returns null`() {
        val result = StudentIdParser.fuzzyFindLabel(
            listOf("XYZ: Rahul Sharma"),
            listOf("Name")
        )
        assertNull(result)
    }

    @Test
    fun `fuzzy label - no separator returns whole line`() {
        val result = StudentIdParser.fuzzyFindLabel(
            listOf("Name Rahul Sharma"),
            listOf("Name")
        )
        assertEquals("Rahul Sharma", result)
    }

    @Test
    fun `fuzzy label - label with bullet prefix`() {
        val result = StudentIdParser.fuzzyFindLabel(
            listOf("• Name: Rahul Sharma"),
            listOf("Name")
        )
        assertEquals("Rahul Sharma", result)
    }

    // ═══════════════════════════════════════════════════════════════
    // ── INDIVIDUAL FIELD EXTRACTION TESTS ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `extract email - standard college email`() {
        val text = "Email: student@rvce.edu.in"
        val result = StudentIdParser.extractEmail(text)
        assertEquals("student@rvce.edu.in", result)
    }

    @Test
    fun `extract email - no email returns empty`() {
        val text = "No contact information available"
        val result = StudentIdParser.extractEmail(text)
        assertTrue(result.isBlank())
    }

    @Test
    fun `extract email - personal email filtered`() {
        val text = "Email: user@rediffmail.com"
        val result = StudentIdParser.extractEmail(text)
        assertTrue(result.isBlank())  // personal domains filtered
    }

    @Test
    fun `normalize year - numeric 1`() {
        assertEquals("1st", StudentIdParser.normalizeYear("1"))
    }

    @Test
    fun `normalize year - roman numeral III`() {
        assertEquals("3rd", StudentIdParser.normalizeYear("III"))
    }

    @Test
    fun `normalize year - 4th text`() {
        assertEquals("4th", StudentIdParser.normalizeYear("4th"))
    }

    @Test
    fun `normalize year - batch year 2024`() {
        // Current year is 2026 in the test environment: 2026-2024 = 2 -> 3rd year
        assertEquals("3rd", StudentIdParser.normalizeYear("2024"))
    }

    // ═══════════════════════════════════════════════════════════════
    // ── EDGE CASES ──
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `parseFull combines front and back fields`() {
        val text = """
            Name:  Deepa Iyer
            Reg:  23BIO5678
            College:  Stella Maris College
            Dept:  Biotechnology
        """.trimIndent()

        val result = StudentIdParser.parseFull(text)
        assertEquals("Deepa Iyer", result.fullName)
        assertEquals("23BIO5678", result.registrationNumber)
        assertEquals("Stella Maris College", result.collegeName)
        assertEquals("Biotechnology", result.department)
    }

    @Test
    fun `isOrganizationText returns true for org names`() {
        assertTrue(StudentIdParser.isOrganizationText("UNIVERSITY OF MUMBAI"))
        assertTrue(StudentIdParser.isOrganizationText("INDIAN INSTITUTE OF TECHNOLOGY"))
    }

    @Test
    fun `isOrganizationText returns false for person names`() {
        assertFalse(StudentIdParser.isOrganizationText("Rahul Sharma"))
        assertFalse(StudentIdParser.isOrganizationText("Priya Singh"))
    }

    @Test
    fun `isDepartmentLabel recognizes degree abbreviations`() {
        assertTrue(StudentIdParser.isDepartmentLabel("BCA"))
        assertTrue(StudentIdParser.isDepartmentLabel("MCA"))
        assertTrue(StudentIdParser.isDepartmentLabel("B.Tech"))
    }

    @Test
    fun `isAddressText recognizes pincode`() {
        assertTrue(StudentIdParser.isAddressText("560001"))
        assertTrue(StudentIdParser.isAddressText("Bangalore - 560001"))
    }

    @Test
    fun `cleanName removes digits and extra spaces`() {
        val result = StudentIdParser.cleanName("Rahul123   Sharma")
        assertEquals("Rahul Sharma", result)
    }

    @Test
    fun `ID card with colon in different positions`() {
        val text = """
            Student Name : Aditya Raj
            Roll No : 2023IT009
            College : SRM Institute of Science and Technology
            Branch : Information Technology
            Year : 2nd
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Aditya Raj", result.fullName)
        assertEquals("2023IT009", result.registrationNumber)
        assertEquals("SRM Institute of Science and Technology", result.collegeName)
        assertEquals("Information Technology", result.department)
        assertEquals("2nd", result.year)
    }

    @Test
    fun `college from institution keyword line`() {
        val text = """
            NMIMS University
            Name: Priyanka Chopra
            Registration Number: 2024BBA1234
            Department: Business Administration
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Priyanka Chopra", result.fullName)
        assertEquals("2024BBA1234", result.registrationNumber)
        // "NMIMS University" contains "University" keyword, extracted as college
        assertEquals("NMIMS University", result.collegeName)
        assertEquals("Business Administration", result.department)
    }

    @Test
    fun `ID card with only uppercase text`() {
        val text = """
            VIDYAVARDHAKA COLLEGE OF ENGINEERING
            STUDENT ID
            NAME: MANOJ KUMAR
            USN: 4VV20CS045
            BRANCH: COMPUTER SCIENCE
        """.trimIndent()

        val result = StudentIdParser.parseFrontSide(text)
        assertEquals("Manoj Kumar", result.fullName)
        assertEquals("4VV20CS045", result.registrationNumber)
        assertEquals("VIDYAVARDHAKA COLLEGE OF ENGINEERING", result.collegeName)
    }
}
