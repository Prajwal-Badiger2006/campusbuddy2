package com.example.campusbuddy.data.local

import android.content.Context

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markVerificationPopupSeen() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_POPUP, true).apply()
    }

    fun hasSeenVerificationPopup(): Boolean {
        return prefs.getBoolean(KEY_HAS_SEEN_POPUP, false)
    }

    fun resetVerificationPopup() {
        prefs.edit().remove(KEY_HAS_SEEN_POPUP).apply()
    }

    fun setVerificationSuccess() {
        prefs.edit().putBoolean(KEY_SHOW_VERIFICATION_SUCCESS, true).apply()
    }

    fun shouldShowVerificationSuccess(): Boolean {
        return prefs.getBoolean(KEY_SHOW_VERIFICATION_SUCCESS, false)
    }

    fun clearVerificationSuccess() {
        prefs.edit().remove(KEY_SHOW_VERIFICATION_SUCCESS).apply()
    }

    companion object {
        private const val PREFS_NAME = "campusbuddy_prefs"
        private const val KEY_HAS_SEEN_POPUP = "has_seen_verification_popup"
        private const val KEY_SHOW_VERIFICATION_SUCCESS = "show_verification_success"
    }
}
