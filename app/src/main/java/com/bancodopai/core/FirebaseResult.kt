package com.bancodopai.core

import com.bancodopai.domain.model.AppError
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import java.io.IOException

internal suspend fun <T> firebaseResult(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toAppError())
    }

internal fun Throwable.toAppError(): AppError {
    var current: Throwable? = this
    while (current != null) {
        if (current is AppError) return current
        current = current.cause
    }

    return when (this) {
        is FirebaseAuthException -> when (errorCode) {
            "ERROR_INVALID_EMAIL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                AppError.Validation("E-mail ou senha inválidos.")

            "ERROR_NETWORK_REQUEST_FAILED" ->
                AppError.Network(this)

            "ERROR_TOO_MANY_REQUESTS" ->
                AppError.Validation("Muitas tentativas. Aguarde um instante.")

            "ERROR_USER_DISABLED" ->
                AppError.Validation("Esta conta está desativada.")

            "ERROR_INVALID_API_KEY" ->
                AppError.Validation("Firebase não configurado. Substitua o google-services.json.")

            else -> AppError.Unknown(this)
        }

        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionDenied
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.Unauthenticated
            FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Network(this)

            else -> AppError.Unknown(this)
        }

        is IOException -> AppError.Network(this)

        else -> AppError.Unknown(this)
    }
}
