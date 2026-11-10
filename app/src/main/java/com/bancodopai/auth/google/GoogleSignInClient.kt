package com.bancodopai.auth.google

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.bancodopai.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInCancelledException : Exception()

/**
 * Encapsula o Credential Manager para obter o ID token do Google.
 * O cliente web vem do google-services.json (criado pelo console do Firebase).
 */
object GoogleSignInClient {

    val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun requestIdToken(context: Context): Result<String> {
        if (!isConfigured) {
            return Result.failure(
                IllegalStateException(
                    "Login com Google ainda não configurado no Firebase."
                )
            )
        }

        val activity = context.findActivity()
            ?: return Result.failure(
                IllegalStateException("Não foi possível abrir o seletor de contas.")
            )

        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val result = CredentialManager
                .create(activity)
                .getCredential(activity, request)

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential
                    .createFrom(credential.data)
                    .idToken
                Result.success(token)
            } else {
                Result.failure(
                    IllegalStateException("Credencial do Google inválida.")
                )
            }
        } catch (error: GetCredentialCancellationException) {
            Result.failure(GoogleSignInCancelledException())
        } catch (error: GetCredentialException) {
            Result.failure(
                IllegalStateException(
                    error.message ?: "Falha ao entrar com Google."
                )
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
