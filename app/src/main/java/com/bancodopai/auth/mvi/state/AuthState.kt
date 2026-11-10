package com.bancodopai.auth.mvi.state

import com.bancodopai.domain.model.User
import com.bancodopai.domain.model.UserRole

data class AuthState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isCheckingSession: Boolean = true,
    val currentUser: User? = null,
    val errorMessage: String? = null,
    val needsProfile: Boolean = false,
    val profileName: String = "",
    val profileEmail: String = "",
    val profileRole: UserRole = UserRole.CHILD,
    val profileInviteCode: String = "",
    val profileError: String? = null,
    val isCreatingProfile: Boolean = false
) {
    val isBusy: Boolean
        get() = isLoading || isGoogleLoading || isCreatingProfile

    val canSubmit: Boolean
        get() = email.isNotBlank() &&
            password.isNotBlank() &&
            !isBusy

    val canSubmitProfile: Boolean
        get() = profileName.isNotBlank() &&
            !isCreatingProfile &&
            (profileRole == UserRole.PARENT || profileInviteCode.isNotBlank())
}
