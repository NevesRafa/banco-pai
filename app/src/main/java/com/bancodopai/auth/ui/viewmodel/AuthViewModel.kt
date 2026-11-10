package com.bancodopai.auth.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.bancodopai.auth.mvi.action.AuthAction
import com.bancodopai.auth.mvi.effect.AuthEffect
import com.bancodopai.auth.mvi.state.AuthState
import com.bancodopai.core.mvi.MviViewModel
import com.bancodopai.core.userMessage
import com.bancodopai.domain.model.GoogleSignInResult
import com.bancodopai.domain.model.User
import com.bancodopai.domain.model.UserRole
import com.bancodopai.domain.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModel(
    private val repository: FinanceRepository
) : MviViewModel<AuthState, AuthAction, AuthEffect>(AuthState()) {

    /**
     * Evita navegar duas vezes quando o AuthStateListener e o retorno do
     * signIn emitem o mesmo usuário.
     */
    private var lastNavigatedUid: String? = null

    init {
        observeSession()
    }

    override fun onAction(action: AuthAction) {
        when (action) {
            is AuthAction.EmailChanged -> updateState {
                it.copy(email = action.value, errorMessage = null)
            }

            is AuthAction.PasswordChanged -> updateState {
                it.copy(password = action.value, errorMessage = null)
            }

            AuthAction.TogglePasswordVisibility -> updateState {
                it.copy(isPasswordVisible = !it.isPasswordVisible)
            }

            AuthAction.Submit -> signIn()

            is AuthAction.GoogleSignIn -> signInWithGoogle(action.idToken)

            is AuthAction.GoogleSignInFailed -> updateState {
                it.copy(isGoogleLoading = false, errorMessage = action.message)
            }

            is AuthAction.ProfileNameChanged -> updateState {
                it.copy(profileName = action.value, profileError = null)
            }

            is AuthAction.ProfileRoleSelected -> updateState {
                it.copy(
                    profileRole = action.role,
                    profileError = null
                )
            }

            is AuthAction.ProfileInviteCodeChanged -> updateState {
                it.copy(
                    profileInviteCode = action.value.uppercase(),
                    profileError = null
                )
            }

            AuthAction.SubmitProfile -> submitProfile()

            AuthAction.DismissError -> updateState {
                it.copy(errorMessage = null, profileError = null)
            }

            AuthAction.SignOut -> signOut()
        }
    }

    private fun observeSession() {
        repository.authState
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) {
                    flowOf(null to null)
                } else {
                    repository.observeUser(uid).map { user -> uid to user }
                }
            }
            .onEach { (uid, user) ->
                updateState {
                    it.copy(isCheckingSession = false, currentUser = user)
                }

                when {
                    user != null -> {
                        updateState {
                            if (it.needsProfile) {
                                it.copy(
                                    needsProfile = false,
                                    profileError = null,
                                    isCreatingProfile = false
                                )
                            } else {
                                it
                            }
                        }
                        navigateIfNeeded(user)
                    }

                    uid != null -> requestProfileIfNeeded()

                    else -> {
                        if (lastNavigatedUid != null) {
                            lastNavigatedUid = null
                            emitEffect(AuthEffect.NavigateToLogin)
                        }
                        updateState {
                            it.copy(needsProfile = false, isCreatingProfile = false)
                        }
                    }
                }
            }
            .catch { error ->
                updateState {
                    it.copy(
                        isCheckingSession = false,
                        errorMessage = error.userMessage()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun requestProfileIfNeeded() {
        val current = currentState
        if (current.needsProfile || current.isCreatingProfile) return

        val name = repository.currentUserName.orEmpty()
        val email = repository.currentUserEmail.orEmpty()

        updateState {
            it.copy(
                needsProfile = true,
                profileName = if (it.profileName.isBlank()) name else it.profileName,
                profileEmail = email,
                profileError = null
            )
        }
        emitEffect(AuthEffect.NavigateToOnboarding)
    }

    private suspend fun navigateIfNeeded(user: User?) {
        if (user == null) {
            if (lastNavigatedUid != null) {
                lastNavigatedUid = null
                emitEffect(AuthEffect.NavigateToLogin)
            }
            return
        }

        if (user.uid == lastNavigatedUid) return

        lastNavigatedUid = user.uid
        emitEffect(AuthEffect.NavigateToHome(user.role))
    }

    private fun signIn() {
        val current = currentState
        if (!current.canSubmit) return

        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }

            repository.signIn(current.email, current.password)
                .onSuccess { user ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            password = "",
                            currentUser = user
                        )
                    }
                    navigateIfNeeded(user)
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.userMessage()
                        )
                    }
                }
        }
    }

    private fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            updateState { it.copy(isGoogleLoading = true, errorMessage = null) }

            repository.signInWithGoogle(idToken)
                .onSuccess { result ->
                    when (result) {
                        is GoogleSignInResult.Existing -> {
                            updateState {
                                it.copy(
                                    isGoogleLoading = false,
                                    currentUser = result.user
                                )
                            }
                            navigateIfNeeded(result.user)
                        }

                        is GoogleSignInResult.NeedsProfile -> {
                            lastNavigatedUid = null
                            updateState {
                                it.copy(
                                    isGoogleLoading = false,
                                    needsProfile = true,
                                    profileName = result.suggestedName,
                                    profileEmail = result.email,
                                    profileError = null
                                )
                            }
                            emitEffect(AuthEffect.NavigateToOnboarding)
                        }
                    }
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = error.userMessage()
                        )
                    }
                }
        }
    }

    private fun submitProfile() {
        val current = currentState
        if (!current.canSubmitProfile) return

        viewModelScope.launch {
            updateState { it.copy(isCreatingProfile = true, profileError = null) }

            repository.createProfile(
                name = current.profileName,
                role = current.profileRole,
                inviteCode = current.profileInviteCode.takeIf { it.isNotBlank() }
            )
                .onSuccess { user ->
                    lastNavigatedUid = user.uid
                    updateState {
                        it.copy(
                            isCreatingProfile = false,
                            needsProfile = false,
                            currentUser = user,
                            profileError = null
                        )
                    }
                    emitEffect(AuthEffect.NavigateToHome(user.role))
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            isCreatingProfile = false,
                            profileError = error.userMessage()
                        )
                    }
                }
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }

            repository.signOut()
                .onSuccess {
                    lastNavigatedUid = null
                    updateState {
                        AuthState(isCheckingSession = false)
                    }
                    emitEffect(AuthEffect.NavigateToLogin)
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.userMessage()
                        )
                    }
                }
        }
    }
}
