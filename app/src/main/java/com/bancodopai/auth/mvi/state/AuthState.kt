package com.bancodopai.auth.mvi.state

import com.bancodopai.domain.model.User

data class AuthState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isCheckingSession: Boolean = true,
    val currentUser: User? = null,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            password.isNotBlank() &&
            !isLoading
}
