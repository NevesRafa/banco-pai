package com.bancodopai.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base MVI:
 * - [state]: estado imutável observável com StateFlow.
 * - [effect]: eventos de uma única vez (navegação, snackbar) com SharedFlow.
 * - [onAction]: único ponto de entrada de intenções da UI.
 */
abstract class MviViewModel<S, A, E>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<E>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effect: SharedFlow<E> = _effect.asSharedFlow()

    protected val currentState: S
        get() = _state.value

    protected fun updateState(reducer: (S) -> S) {
        _state.update(reducer)
    }

    protected suspend fun emitEffect(effect: E) {
        _effect.emit(effect)
    }

    abstract fun onAction(action: A)
}
