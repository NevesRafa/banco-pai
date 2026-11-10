package com.bancodopai.child.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.bancodopai.child.mvi.action.ChildHomeAction
import com.bancodopai.child.mvi.effect.ChildHomeEffect
import com.bancodopai.child.mvi.state.ChildHomeState
import com.bancodopai.core.mvi.MviViewModel
import com.bancodopai.core.toCentsOrNull
import com.bancodopai.core.userMessage
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.TransactionStatus
import com.bancodopai.domain.model.TransactionType
import com.bancodopai.domain.model.User
import com.bancodopai.domain.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ChildHomeViewModel(
    private val repository: FinanceRepository
) : MviViewModel<ChildHomeState, ChildHomeAction, ChildHomeEffect>(ChildHomeState()) {

    init {
        observeHome()
    }

    override fun onAction(action: ChildHomeAction) {
        when (action) {
            ChildHomeAction.OpenWithdrawSheet -> updateState {
                it.copy(isWithdrawSheetOpen = true, withdrawalError = null)
            }

            ChildHomeAction.CloseWithdrawSheet -> updateState {
                it.copy(isWithdrawSheetOpen = false, withdrawalError = null)
            }

            is ChildHomeAction.WithdrawalAmountChanged -> updateState {
                it.copy(withdrawalAmount = action.value, withdrawalError = null)
            }

            is ChildHomeAction.WithdrawalReasonChanged -> updateState {
                it.copy(withdrawalReason = action.value, withdrawalError = null)
            }

            ChildHomeAction.SubmitWithdrawal -> submitWithdrawal()

            ChildHomeAction.DismissError -> updateState {
                it.copy(errorMessage = null)
            }

            ChildHomeAction.OpenGoalSheet -> updateState {
                it.copy(isGoalSheetOpen = true, goalError = null)
            }

            ChildHomeAction.CloseGoalSheet -> updateState {
                it.copy(isGoalSheetOpen = false, goalError = null)
            }

            is ChildHomeAction.SubmitGoal -> submitGoal(action.name, action.target)
        }
    }

    private fun submitGoal(name: String, targetText: String) {
        val child = currentState.child ?: return

        if (name.isBlank()) {
            updateState { it.copy(goalError = "Dê um nome para a meta.") }
            return
        }

        val targetCents = targetText.toCentsOrNull()
        if (targetCents == null || targetCents <= 0L) {
            updateState { it.copy(goalError = "Informe um valor maior que zero.") }
            return
        }

        viewModelScope.launch {
            updateState { it.copy(isSavingGoal = true, goalError = null) }

            repository.updateGoal(
                childId = child.uid,
                goalName = name.trim(),
                goalTargetCents = targetCents
            )
                .onSuccess {
                    updateState {
                        it.copy(isSavingGoal = false, isGoalSheetOpen = false)
                    }
                    emitEffect(ChildHomeEffect.ShowMessage("Meta atualizada."))
                }
                .onFailure { error ->
                    updateState {
                        it.copy(isSavingGoal = false, goalError = error.userMessage())
                    }
                }
        }
    }

    private fun observeHome() {
        repository.authState
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) {
                    flowOf<ChildData?>(null)
                } else {
                    combine(
                        repository.observeUser(uid),
                        repository.observeTransactions(uid)
                    ) { user, transactions ->
                        ChildData(user, transactions)
                    }
                }
            }
            .onEach { data ->
                if (data == null) {
                    updateState {
                        it.copy(
                            isLoading = false,
                            child = null,
                            transactions = emptyList(),
                            pendingWithdrawals = emptyList(),
                            weeklyInterestCents = 0L
                        )
                    }
                    return@onEach
                }

                val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)

                val pending = data.transactions.filter {
                    it.type == TransactionType.WITHDRAW &&
                        it.status == TransactionStatus.PENDING
                }

                val weeklyInterest = data.transactions
                    .filter {
                        it.type == TransactionType.INTEREST &&
                            it.status == TransactionStatus.APPROVED &&
                            it.timestamp.isAfter(weekAgo)
                    }
                    .sumOf { it.amountCents }

                updateState {
                    it.copy(
                        isLoading = false,
                        child = data.user,
                        transactions = data.transactions,
                        pendingWithdrawals = pending,
                        weeklyInterestCents = weeklyInterest,
                        errorMessage = null
                    )
                }
            }
            .catch { error ->
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.userMessage()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun submitWithdrawal() {
        val current = currentState
        val child = current.child ?: return
        val amountCents = current.withdrawalAmount.toCentsOrNull()

        if (amountCents == null || amountCents <= 0L) {
            updateState {
                it.copy(withdrawalError = "Informe um valor válido.")
            }
            return
        }

        if (current.withdrawalReason.isBlank()) {
            updateState {
                it.copy(withdrawalError = "Conte para que você quer usar o dinheiro.")
            }
            return
        }

        viewModelScope.launch {
            updateState {
                it.copy(isSubmittingWithdrawal = true, withdrawalError = null)
            }

            repository.requestWithdrawal(
                childId = child.uid,
                amountCents = amountCents,
                description = current.withdrawalReason.trim()
            )
                .onSuccess {
                    updateState {
                        it.copy(
                            isSubmittingWithdrawal = false,
                            isWithdrawSheetOpen = false,
                            withdrawalAmount = "",
                            withdrawalReason = ""
                        )
                    }
                    emitEffect(ChildHomeEffect.WithdrawalRequested)
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            isSubmittingWithdrawal = false,
                            withdrawalError = error.userMessage()
                        )
                    }
                }
        }
    }

    private data class ChildData(
        val user: User?,
        val transactions: List<Transaction>
    )
}
