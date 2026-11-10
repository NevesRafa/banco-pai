package com.bancodopai.auth.mvi.action

sealed interface AuthAction {
    data class EmailChanged(val value: String) : AuthAction
    data class PasswordChanged(val value: String) : AuthAction
    data object TogglePasswordVisibility : AuthAction
    data object Submit : AuthAction
    data object DismissError : AuthAction
    data object SignOut : AuthAction
}
