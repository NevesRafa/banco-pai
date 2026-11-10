package com.bancodopai.parent.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.bancodopai.core.mvi.MviViewModel
import com.bancodopai.core.toBrl
import com.bancodopai.core.toCentsOrNull
import com.bancodopai.core.userMessage
import com.bancodopai.domain.model.DepositReason
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.User
import com.bancodopai.domain.repository.FinanceRepository
import com.bancodopai.parent.mvi.action.ParentHomeAction
import com.bancodopai.parent.mvi.effect.ParentHomeEffect
import com.bancodopai.parent.mvi.state.ParentHomeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ParentHomeViewModel(
    private val repository: FinanceRepository
) : MviViewModel<ParentHomeState, ParentHomeAction, ParentHomeEffect>(ParentHomeState()) {

    private var dueInterestJob: Job? = null
    private var inviteRequested = false
    private var linkedChildrenKey: String? = null
    private val syncedFamilyKeys = mutableSetOf<String>()

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

            is ParentHomeAction.SelectChild -> selectChild(action.childId)

            ParentHomeAction.OpenGoalSheet -> updateState {
                it.copy(isGoalSheetOpen = true, goalError = null)
            }

            ParentHomeAction.CloseGoalSheet -> updateState {
                it.copy(isGoalSheetOpen = false, goalError = null)
            }

            is ParentHomeAction.SubmitGoal -> submitGoal(action.name, action.target)
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
                    emitEffect(ParentHomeEffect.ShowMessage("Meta de ${child.name} atualizada."))
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
                    flowOf<ParentData?>(null)
                } else {
                    repository.observeUser(uid).flatMapLatest { parent ->
                        if (parent == null) {
                            flowOf(ParentData(null, emptyList(), emptyList()))
                        } else {
                            combine(
                                repository.observeChildrenOfParent(parent.uid),
                                repository.observeFamilyTransactions(parent.uid)
                            ) { children, transactions ->
                                ParentData(parent, children, transactions)
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
                            children = emptyList(),
                            familyTransactions = emptyList()
                        )
                    }
                    return@onEach
                }

                updateState {
                    val selected = it.selectedChildId
                        ?.takeIf { id -> data.children.any { child -> child.uid == id } }
                        ?: data.children.firstOrNull()?.uid
                    val focus = data.children.firstOrNull { child -> child.uid == selected }

                    it.copy(
                        isLoading = false,
                        parent = data.parent,
                        children = data.children,
                        selectedChildId = selected,
                        familyTransactions = data.transactions,
                        errorMessage = null,
                        interestRateInput = if (focus != null && it.interestRateInput.isBlank() &&
                            focus.weeklyInterestRatePercent > 0.0
                        ) {
                            formatRate(focus.weeklyInterestRatePercent)
                        } else {
                            it.interestRateInput
                        }
                    )
                }

                ensureInviteCode(data.parent)
                linkInviteToChildren(data.parent, data.children)
                syncFamilyTransactions(data.parent, data.children)
                applyDueInterest(data.children)
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

    private fun selectChild(childId: String) {
        updateState {
            val focus = it.children.firstOrNull { child -> child.uid == childId }
            it.copy(
                selectedChildId = childId,
                interestRateInput = if (focus != null && focus.weeklyInterestRatePercent > 0.0) {
                    formatRate(focus.weeklyInterestRatePercent)
                } else {
                    ""
                }
            )
        }
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
                            "Taxa de ${child.name} atualizada para ${formatRate(percent)}% por semana."
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

    private fun linkInviteToChildren(parent: User?, children: List<User>) {
        if (parent == null || !parent.isFamilyHead) return
        val code = parent.inviteCode ?: return
        val ids = children.map { it.uid }.sorted()
        val key = ids.joinToString(",")
        if (linkedChildrenKey == key) return
        viewModelScope.launch {
            repository.linkInviteToChildren(code, ids)
                .onSuccess { linkedChildrenKey = key }
        }
    }

    private fun syncFamilyTransactions(parent: User?, children: List<User>) {
        if (parent == null) return
        children.filter { it.familyIds.size > 1 }.forEach { child ->
            val key = child.uid + child.familyIds.sorted().joinToString(",")
            if (!syncedFamilyKeys.add(key)) return@forEach
            viewModelScope.launch {
                repository.syncFamilyTransactions(childId = child.uid, parentId = parent.uid)
            }
        }
    }

    private fun applyDueInterest(children: List<User>) {
        if (dueInterestJob?.isActive == true) return
        val due = children.filter {
            it.weeklyInterestRatePercent > 0.0 && it.lastInterestAppliedAt != null
        }
        if (due.isEmpty()) return

        dueInterestJob = viewModelScope.launch {
            due.forEach { child ->
                repository.applyDueInterest(childId = child.uid)
                    .onSuccess { transaction ->
                        if (transaction != null) {
                            emitEffect(
                                ParentHomeEffect.ShowMessage(
                                    "Rendimento semanal de ${child.name}: " +
                                        transaction.amountCents.toBrl() + "."
                                )
                            )
                        }
                    }
            }
        }
    }

    private fun formatRate(percent: Double): String =
        percent.toString().removeSuffix(".0").replace('.', ',')

    private data class ParentData(
        val parent: User?,
        val children: List<User>,
        val transactions: List<Transaction>
    )
}
