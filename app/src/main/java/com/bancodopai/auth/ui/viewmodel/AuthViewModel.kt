package com.bancodopai.auth.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.bancodopai.auth.mvi.action.AuthAction
import com.bancodopai.auth.mvi.effect.AuthEffect
import com.bancodopai.auth.mvi.state.AuthState
import com.bancodopai.core.mvi.MviViewModel
import com.bancodopai.core.userMessage
import com.bancodopai.domain.model.User
import com.bancodopai.domain.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
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

            AuthAction.DismissError -> updateState {
                it.copy(errorMessage = null)
            }

            AuthAction.SignOut -> signOut()
        }
    }

    private fun observeSession() {
        repository.authState
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) {
                    flowOf(null)
                } else {
                    repository.observeUser(uid)
                }
            }
            .onEach { user ->
                updateState {
                    it.copy(
                        isCheckingSession = false,
                        currentUser = user
                    )
                }
                navigateIfNeeded(user)
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

    private suspend fun navigateIfNeeded(user: User?) {
        if (user == null) {
            lastNavigatedUid = null
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

    private fun signOut() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }

            repository.signOut()
                .onSuccess {
                    lastNavigatedUid = null
                    updateState {
                        AuthState(isCheckingSession = false)
                    }
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
