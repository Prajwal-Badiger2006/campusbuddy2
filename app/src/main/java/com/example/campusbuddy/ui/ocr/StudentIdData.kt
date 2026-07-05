package com.example.campusbuddy.ui.ocr

data class StudentIdData(
    val fullName: String = "",
    val registrationNumber: String = "",
    val email: String = "",
    val collegeName: String = "",
    val department: String = "",
    val year: String = "",
    val frontScanned: Boolean = false,
    val backScanned: Boolean = false
) {
    val isComplete: Boolean get() = frontScanned && backScanned

    val hasAnyData: Boolean get() =
        fullName.isNotBlank() || registrationNumber.isNotBlank() ||
        email.isNotBlank() || collegeName.isNotBlank() ||
        department.isNotBlank() || year.isNotBlank()
}
