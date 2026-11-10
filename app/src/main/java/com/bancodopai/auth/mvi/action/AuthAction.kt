package com.bancodopai.auth.mvi.action

import com.bancodopai.domain.model.UserRole

sealed interface AuthAction {
    data class EmailChanged(val value: String) : AuthAction
    data class PasswordChanged(val value: String) : AuthAction
    data object TogglePasswordVisibility : AuthAction
    data object Submit : AuthAction
    data class GoogleSignIn(val idToken: String) : AuthAction
    data class GoogleSignInFailed(val message: String) : AuthAction
    data class ProfileNameChanged(val value: String) : AuthAction
    data class ProfileRoleSelected(val role: UserRole) : AuthAction
    data class ProfileInviteCodeChanged(val value: String) : AuthAction
    data object SubmitProfile : AuthAction
    data object DismissError : AuthAction
    data object SignOut : AuthAction
}
