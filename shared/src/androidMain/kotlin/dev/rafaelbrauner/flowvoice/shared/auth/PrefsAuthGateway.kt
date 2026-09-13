package dev.rafaelbrauner.flowvoice.shared.auth

import android.content.Context

class PrefsAuthGateway(context: Context) : AuthGateway {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun currentUser(): AuthUser? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        if (id.isBlank() || email.isBlank()) return null
        return AuthUser(id, email)
    }

    override fun signIn(user: AuthUser) {
        prefs.edit().putString(KEY_ID, user.id).putString(KEY_EMAIL, user.email).apply()
    }

    override fun signOut() {
        prefs.edit().remove(KEY_ID).remove(KEY_EMAIL).apply()
    }

    companion object {
        private const val PREFS = "flowvoice_auth"
        private const val KEY_ID = "user_id"
        private const val KEY_EMAIL = "user_email"
    }
}
