package com.example.campusbuddy

import com.example.campusbuddy.ui.ocr.StudentIdParser
import org.junit.Test

class DiagnosticTest {

    @Test
    fun `diagnose all failing cases`() {
        // Test 1: labeled fields
        val text1 = """
            Student Name:  Rahul Sharma
            Reg No:  2021BCS0042
            College:  Bangalore Institute of Technology
            Department:  Computer Science
            Year:  3rd
        """.trimIndent()
        val r1 = StudentIdParser.parseFrontSide(text1)
        println("=== Test 1: labeled fields ===")
        println("fullName='${r1.fullName}' expected='Rahul Sharma'")
        println("reg='${r1.registrationNumber}' expected='2021BCS0042'")
        println("college='${r1.collegeName}' expected='Bangalore Institute of Technology'")
        println("dept='${r1.department}' expected='Computer Science'")
        println("year='${r1.year}' expected='3rd'")

        // Test 2: uppercase name
        val text2 = """
            PES UNIVERSITY
            Student ID Card
            PRIYA SINGH
            USN: 01PE22CS101
            Branch: CSE
            2022-2026
        """.trimIndent()
        val r2 = StudentIdParser.parseFrontSide(text2)
        println("\n=== Test 2: uppercase name ===")
        println("fullName='${r2.fullName}' expected='Priya Singh'")
        println("reg='${r2.registrationNumber}' expected='01PE22CS101'")
        println("college='${r2.collegeName}' expected='PES UNIVERSITY'")
        println("dept='${r2.department}' expected='CSE'")

        // Test 3: Nane instead of Name
        val text3 = """
            Nane:  Vikram Patel
            Reg:  2022MCA008
            College:  Christ University
            Dept:  MCA
            Year: 2
        """.trimIndent()
        val r3 = StudentIdParser.parseFrontSide(text3)
        println("\n=== Test 3: Nane -> Name ===")
        println("fullName='${r3.fullName}' expected='Vikram Patel'")
        println("reg='${r3.registrationNumber}' expected='2022MCA008'")
        println("college='${r3.collegeName}' expected='Christ University'")
        println("dept='${r3.department}' expected='MCA'")
        println("year='${r3.year}' expected='2nd'")

        // Test 4: Dpartment
        val text4 = """
            Roll No: 2024BCA045
            Nane: Anjali Gupta
            Dpartment: BCA
            College: Mount Carmel College
            Batch: 2024
        """.trimIndent()
        val r4 = StudentIdParser.parseFrontSide(text4)
        println("\n=== Test 4: Dpartment ===")
        println("fullName='${r4.fullName}' expected='Anjali Gupta'")
        println("reg='${r4.registrationNumber}' expected='2024BCA045'")
        println("college='${r4.collegeName}' expected='Mount Carmel College'")
        println("dept='${r4.department}' expected='BCA'")
        println("year='${r4.year}' expected='3rd'")

        // Test 5: minimal
        val text5 = """
            Name:  Ravi Deshmukh
            Reg: 23BCS7890
            College: RV College of Engineering
        """.trimIndent()
        val r5 = StudentIdParser.parseFrontSide(text5)
        println("\n=== Test 5: minimal ===")
        println("fullName='${r5.fullName}' expected='Ravi Deshmukh'")
        println("reg='${r5.registrationNumber}' expected='23BCS7890'")
        println("college='${r5.collegeName}' expected='RV College of Engineering'")

        // Test 6: levenshtein
        println("\n=== Test 6: levenshtein ===")
        println("lev('Dpartment','Department')=${StudentIdParser.levenshteinDistance("Dpartment", "Department")} expected=1")
        println("lev('Nane','Name')=${StudentIdParser.levenshteinDistance("Nane", "Name")} expected=1")

        // Test 7: isDepartmentLabel
        println("\n=== Test 7: isDepartmentLabel ===")
        println("isDepartmentLabel('BCA')=${StudentIdParser.isDepartmentLabel("BCA")} expected=true")
        println("isDepartmentLabel('MCA')=${StudentIdParser.isDepartmentLabel("MCA")} expected=true")

        // Test 8: college from institution keyword
        val text8 = """
            NMIMS University
            Name: Priyanka Chopra
            Registration Number: 2024BBA1234
            Department: Business Administration
        """.trimIndent()
        val r8 = StudentIdParser.parseFrontSide(text8)
        println("\n=== Test 8: college from institution keyword ===")
        println("fullName='${r8.fullName}' expected='Priyanka Chopra'")
        println("reg='${r8.registrationNumber}' expected='2024BBA1234'")
        println("college='${r8.collegeName}' expected='NMIMS University'")
        println("dept='${r8.department}' expected='Business Administration'")

        // Test 9: address lines
        val text9 = """
            REVA UNIVERSITY
            Student Identity Card
            
            Name:  Arjun Kumar
            Register Number: 21MCA0231
            Programme: MBA
            Year of Study: 2024
            
            Address: 123, MG Road, Bangalore - 560001
            Blood Group: O+
        """.trimIndent()
        val r9 = StudentIdParser.parseFrontSide(text9)
        println("\n=== Test 9: address lines ===")
        println("fullName='${r9.fullName}' expected='Arjun Kumar'")
        println("reg='${r9.registrationNumber}' expected='21MCA0231'")
        println("college='${r9.collegeName}' expected='REVA UNIVERSITY'")
        println("dept='${r9.department}' expected='MBA'")
        println("year='${r9.year}' expected='3rd'")

        // Test 10: colon in different positions
        val text10 = """
            Student Name : Aditya Raj
            Roll No : 2023IT009
            College : SRM Institute of Science and Technology
            Branch : Information Technology
            Year : 2nd
        """.trimIndent()
        val r10 = StudentIdParser.parseFrontSide(text10)
        println("\n=== Test 10: colon in different positions ===")
        println("fullName='${r10.fullName}' expected='Aditya Raj'")
        println("reg='${r10.registrationNumber}' expected='2023IT009'")
        println("college='${r10.collegeName}' expected='SRM Institute of Science and Technology'")
        println("dept='${r10.department}' expected='Information Technology'")
        println("year='${r10.year}' expected='2nd'")

        // Test 11: uppercase only text
        val text11 = """
            VIDYAVARDHAKA COLLEGE OF ENGINEERING
            STUDENT ID
            NAME: MANOJ KUMAR
            USN: 4VV20CS045
            BRANCH: COMPUTER SCIENCE
        """.trimIndent()
        val r11 = StudentIdParser.parseFrontSide(text11)
        println("\n=== Test 11: uppercase only text ===")
        println("fullName='${r11.fullName}' expected='MANOJ KUMAR' or 'Manoj Kumar'")
        println("reg='${r11.registrationNumber}' expected='4VV20CS045'")
        println("college='${r11.collegeName}' expected='VIDYAVARDHAKA COLLEGE OF ENGINEERING'")

        // Test 12: back side - email and year
        val frontData = com.example.campusbuddy.ui.ocr.StudentIdData(
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
        val r12 = StudentIdParser.parseBackSide(backText, frontData)
        println("\n=== Test 12: back side - email ===")
        println("email='${r12.email}' expected='kiran.joshi@mitcollege.edu'")
        println("year='${r12.year}' expected='3rd'")
        println("fullName='${r12.fullName}' expected='Kiran Joshi'")

        // Test 13: back side - department from back only
        val frontData2 = com.example.campusbuddy.ui.ocr.StudentIdData(
            fullName = "Neha Patel",
            registrationNumber = "22BBA1010",
            collegeName = "St. Joseph's College",
            frontScanned = true
        )
        val backText2 = """
            Department: Business Administration
            Year: 2nd
        """.trimIndent()
        val r13 = StudentIdParser.parseBackSide(backText2, frontData2)
        println("\n=== Test 13: back side - dept from back ===")
        println("dept='${r13.department}' expected='Business Administration'")
        println("year='${r13.year}' expected='2nd'")

        // Test 14: parseFull
        val text14 = """
            Name:  Deepa Iyer
            Reg:  23BIO5678
            College:  Stella Maris College
            Dept:  Biotechnology
        """.trimIndent()
        val r14 = StudentIdParser.parseFull(text14)
        println("\n=== Test 14: parseFull ===")
        println("fullName='${r14.fullName}' expected='Deepa Iyer'")
        println("reg='${r14.registrationNumber}' expected='23BIO5678'")
        println("college='${r14.collegeName}' expected='Stella Maris College'")
        println("dept='${r14.department}' expected='Biotechnology'")

        // Test 15: ID card with email
        val text15 = """
            Name: Sneha Reddy
            Reg No: 22BEC0456
            Email: sneha.reddy@bmsce.ac.in
            College: BMS College of Engineering
            Branch: ECE
        """.trimIndent()
        val r15 = StudentIdParser.parseFull(text15)
        println("\n=== Test 15: ID card with email ===")
        println("fullName='${r15.fullName}' expected='Sneha Reddy'")
        println("reg='${r15.registrationNumber}' expected='22BEC0456'")
        println("email='${r15.email}' expected='sneha.reddy@bmsce.ac.in'")
        println("college='${r15.collegeName}' expected='BMS College of Engineering'")
    }
}
