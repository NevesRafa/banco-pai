package com.bancodopai.parent.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.bancodopai.core.mvi.MviViewModel
import com.bancodopai.core.toBrl
import com.bancodopai.core.toCentsOrNull
import com.bancodopai.core.userMessage
import com.bancodopai.domain.model.DepositReason
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.TransactionStatus
import com.bancodopai.domain.model.TransactionType
import com.bancodopai.domain.model.User
import com.bancodopai.domain.repository.FinanceRepository
import com.bancodopai.parent.mvi.action.ParentHomeAction
import com.bancodopai.parent.mvi.effect.ParentHomeEffect
import com.bancodopai.parent.mvi.state.ParentHomeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ParentHomeViewModel(
    private val repository: FinanceRepository
) : MviViewModel<ParentHomeState, ParentHomeAction, ParentHomeEffect>(ParentHomeState()) {

    private var dueInterestJob: Job? = null
    private var inviteRequested = false
    private var linkedChildId: String? = null
    private var syncedFamilyKey: String? = null

    init {
        observeHome()
    }

    override fun onAction(action: ParentHomeAction) {
        when (action) {
            ParentHomeAction.OpenDepositSheet -> updateState {
                it.copy(isDepositSheetOpen = true, depositError = null)
            }

            ParentHomeAction.CloseDepositSheet -> updateState {
                it.copy(isDepositSheetOpen = false, depositError = null)
            }

            is ParentHomeAction.DepositAmountChanged -> updateState {
                it.copy(depositAmount = action.value, depositError = null)
            }

            is ParentHomeAction.DepositReasonSelected -> updateState {
                it.copy(depositReason = action.reason, depositError = null)
            }

            is ParentHomeAction.DepositNoteChanged -> updateState {
                it.copy(depositNote = action.value, depositError = null)
            }

            ParentHomeAction.SubmitDeposit -> submitDeposit()

            is ParentHomeAction.ApproveWithdrawal ->
                processWithdrawal(action.transactionId, approve = true)

            is ParentHomeAction.DenyWithdrawal ->
                processWithdrawal(action.transactionId, approve = false)

            ParentHomeAction.DismissError -> updateState {
                it.copy(errorMessage = null)
            }

            is ParentHomeAction.InterestRateChanged -> updateState {
                it.copy(interestRateInput = action.value)
            }

            ParentHomeAction.SaveInterestRate -> saveInterestRate()

            ParentHomeAction.ApplyInterestNow -> applyInterestNow()
        }
    }

    private fun observeHome() {
        repository.authState
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) {
                    flowOf<ParentData?>(null)
                } else {
                    repository.observeUser(uid).flatMapLatest { parent ->
                        if (parent == null) {
                            flowOf(ParentData(null, null, emptyList()))
                        } else {
                            repository.observeChildOfParent(parent.uid)
                                .flatMapLatest { child ->
                                    if (child == null) {
                                        flowOf(ParentData(parent, null, emptyList()))
                                    } else {
                                        repository.observeTransactions(
                                            childId = child.uid,
                                            parentId = parent.uid
                                        )
                                            .map { transactions ->
                                                ParentData(parent, child, transactions)
                                            }
                                    }
                                }
                        }
                    }
                }
            }
            .onEach { data ->
                if (data == null) {
                    updateState {
                        it.copy(
                            isLoading = false,
                            parent = null,
                            child = null,
                            transactions = emptyList(),
                            pendingWithdrawals = emptyList()
                        )
                    }
                    return@onEach
                }

                val pending = data.transactions.filter {
                    it.type == TransactionType.WITHDRAW &&
                        it.status == TransactionStatus.PENDING
                }

                updateState {
                    it.copy(
                        isLoading = false,
                        parent = data.parent,
                        child = data.child,
                        transactions = data.transactions,
                        pendingWithdrawals = pending,
                        errorMessage = null,
                        interestRateInput = if (it.interestRateInput.isBlank() &&
                            (data.child?.weeklyInterestRatePercent ?: 0.0) > 0.0
                        ) {
                            formatRate(data.child?.weeklyInterestRatePercent ?: 0.0)
                        } else {
                            it.interestRateInput
                        }
                    )
                }

                ensureInviteCode(data.parent)
                linkInviteToChild(data.parent, data.child)
                syncFamilyTransactions(data.parent, data.child)
                applyDueInterest(data.child)
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

    private fun submitDeposit() {
        val current = currentState
        val child = current.child ?: return
        val amountCents = current.depositAmount.toCentsOrNull()

        if (amountCents == null || amountCents <= 0L) {
            updateState {
                it.copy(depositError = "Informe um valor válido.")
            }
            return
        }

        if (current.depositReason == DepositReason.OUTROS &&
            current.depositNote.isBlank()
        ) {
            updateState {
                it.copy(depositError = "Descreva o motivo do depósito.")
            }
            return
        }

        val note = current.depositNote.trim()
        val description = when {
            current.depositReason == DepositReason.OUTROS -> note
            note.isBlank() -> current.depositReason.label
            else -> "${current.depositReason.label} — $note"
        }

        viewModelScope.launch {
            updateState {
                it.copy(isSubmittingDeposit = true, depositError = null)
            }

            repository.deposit(
                childId = child.uid,
                amountCents = amountCents,
                description = description
            )
                .onSuccess {
                    updateState {
                        it.copy(
                            isSubmittingDeposit = false,
                            isDepositSheetOpen = false,
                            depositAmount = "",
                            depositNote = "",
                            depositReason = DepositReason.MESADA
                        )
                    }
                    emitEffect(ParentHomeEffect.DepositCompleted)
                }
                .onFailure { error ->
                    updateState {
                        it.copy(
                            isSubmittingDeposit = false,
                            depositError = error.userMessage()
                        )
                    }
                }
        }
    }

    private fun processWithdrawal(transactionId: String, approve: Boolean) {
        viewModelScope.launch {
            updateState {
                it.copy(processingTransactionId = transactionId)
            }

            val result = if (approve) {
                repository.approveWithdrawal(transactionId)
            } else {
                repository.denyWithdrawal(transactionId)
            }

            result
                .onSuccess {
                    emitEffect(
                        if (approve) {
                            ParentHomeEffect.WithdrawalApproved
                        } else {
                            ParentHomeEffect.WithdrawalDenied
                        }
                    )
                }
                .onFailure { error ->
                    emitEffect(ParentHomeEffect.ShowMessage(error.userMessage()))
                }

            updateState {
                it.copy(processingTransactionId = null)
            }
        }
    }

    private fun saveInterestRate() {
        val current = currentState
        val child = current.child ?: return
        val percent = current.interestRateInput
            .replace(',', '.')
            .trim()
            .toDoubleOrNull()

        if (percent == null || percent < 0.0 || percent > 100.0) {
            viewModelScope.launch {
                emitEffect(ParentHomeEffect.ShowMessage("Informe uma taxa entre 0 e 100."))
            }
            return
        }

        viewModelScope.launch {
            updateState { it.copy(isSavingInterestRate = true) }

            repository.updateInterestRate(
                childId = child.uid,
                percentPerWeek = percent
            )
                .onSuccess {
                    emitEffect(
                        ParentHomeEffect.ShowMessage(
                            "Taxa atualizada para ${formatRate(percent)}% por semana."
                        )
                    )
                }
                .onFailure { error ->
                    emitEffect(ParentHomeEffect.ShowMessage(error.userMessage()))
                }

            updateState { it.copy(isSavingInterestRate = false) }
        }
    }

    private fun applyInterestNow() {
        val current = currentState
        val child = current.child ?: return
        if (current.isApplyingInterest) return

        viewModelScope.launch {
            updateState { it.copy(isApplyingInterest = true) }

            repository.applyInterestNow(childId = child.uid)
                .onSuccess { transaction ->
                    val message = if (transaction == null) {
                        "Nada a creditar: saldo zerado ou taxa em zero."
                    } else {
                        "Rendimento de ${transaction.amountCents.toBrl()} aplicado."
                    }
                    emitEffect(ParentHomeEffect.ShowMessage(message))
                }
                .onFailure { error ->
                    emitEffect(ParentHomeEffect.ShowMessage(error.userMessage()))
                }

            updateState { it.copy(isApplyingInterest = false) }
        }
    }

    private fun ensureInviteCode(parent: User?) {
        if (parent == null || !parent.isFamilyHead) return
        if (parent.inviteCode != null || inviteRequested) return
        inviteRequested = true
        viewModelScope.launch {
            repository.ensureInviteCode(parent.uid)
        }
    }

    private fun linkInviteToChild(parent: User?, child: User?) {
        if (parent == null || child == null || !parent.isFamilyHead) return
        val code = parent.inviteCode ?: return
        if (linkedChildId == child.uid) return
        viewModelScope.launch {
            repository.linkInviteToChild(code, child.uid)
                .onSuccess { linkedChildId = child.uid }
        }
    }

    private fun syncFamilyTransactions(parent: User?, child: User?) {
        if (parent == null || child == null) return
        if (child.familyIds.size < 2) return
        val key = child.uid + child.familyIds.sorted().joinToString(",")
        if (syncedFamilyKey == key) return
        syncedFamilyKey = key
        viewModelScope.launch {
            repository.syncFamilyTransactions(childId = child.uid, parentId = parent.uid)
        }
    }

    private fun applyDueInterest(child: User?) {
        if (child == null) return
        if (child.weeklyInterestRatePercent <= 0.0) return
        if (child.lastInterestAppliedAt == null) return
        if (dueInterestJob?.isActive == true) return

        dueInterestJob = viewModelScope.launch {
            repository.applyDueInterest(childId = child.uid)
                .onSuccess { transaction ->
                    if (transaction != null) {
                        emitEffect(
                            ParentHomeEffect.ShowMessage(
                                "Rendimento semanal aplicado: ${transaction.amountCents.toBrl()}."
                            )
                        )
                    }
                }
        }
    }

    private fun formatRate(percent: Double): String =
        percent.toString().removeSuffix(".0").replace('.', ',')

    private data class ParentData(
        val parent: User?,
        val child: User?,
        val transactions: List<Transaction>
    )
}
