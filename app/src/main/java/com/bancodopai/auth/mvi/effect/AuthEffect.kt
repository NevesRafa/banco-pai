package com.bancodopai.auth.mvi.effect

import com.bancodopai.domain.model.UserRole

sealed interface AuthEffect {
    data class NavigateToHome(val role: UserRole) : AuthEffect
    data object NavigateToOnboarding : AuthEffect
    data object NavigateToLogin : AuthEffect
    data class ShowMessage(val message: String) : AuthEffect
}
