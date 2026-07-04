package com.example.campusbuddy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Outfit font from Google Fonts — download and place in res/font/:
// https://fonts.google.com/specimen/Outfit
// Files needed: outfit_regular.ttf, outfit_medium.ttf, outfit_semibold.ttf,
//               outfit_bold.ttf, outfit_extrabold.ttf
// Once added, uncomment below and replace FontFamily.Default:
//
// val OutfitFontFamily = FontFamily(
//     Font(R.font.outfit_regular, FontWeight.Normal),
//     Font(R.font.outfit_medium, FontWeight.Medium),
//     Font(R.font.outfit_semibold, FontWeight.SemiBold),
//     Font(R.font.outfit_bold, FontWeight.Bold),
//     Font(R.font.outfit_extrabold, FontWeight.ExtraBold)
// )

private val DefaultFontFamily = FontFamily.Default
val MonospaceFontFamily = FontFamily.Monospace

val CampusBuddyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 56.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 40.sp,
        letterSpacing = (-0.01).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 28.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        letterSpacing = 0.05.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonospaceFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )
)
