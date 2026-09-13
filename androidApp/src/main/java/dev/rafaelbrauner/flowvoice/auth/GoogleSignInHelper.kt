package dev.rafaelbrauner.flowvoice.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.rafaelbrauner.flowvoice.shared.auth.AuthUser

object GoogleSignInHelper {
    suspend fun signIn(activity: Activity, webClientId: String): AuthUser {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val google = GoogleIdTokenCredential.createFrom(result.credential.data)
        val email = google.id
        return AuthUser(id = email, email = email)
    }
}
