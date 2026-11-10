package com.bancodopai.parent.mvi.action

import com.bancodopai.domain.model.DepositReason

sealed interface ParentHomeAction {
    data object OpenDepositSheet : ParentHomeAction
    data object CloseDepositSheet : ParentHomeAction
    data class DepositAmountChanged(val value: String) : ParentHomeAction
    data class DepositReasonSelected(val reason: DepositReason) : ParentHomeAction
    data class DepositNoteChanged(val value: String) : ParentHomeAction
    data object SubmitDeposit : ParentHomeAction
    data class ApproveWithdrawal(val transactionId: String) : ParentHomeAction
    data class DenyWithdrawal(val transactionId: String) : ParentHomeAction
    data class InterestRateChanged(val value: String) : ParentHomeAction
    data object SaveInterestRate : ParentHomeAction
    data object ApplyInterestNow : ParentHomeAction
    data class SelectChild(val childId: String) : ParentHomeAction
    data object OpenGoalSheet : ParentHomeAction
    data object CloseGoalSheet : ParentHomeAction
    data class SubmitGoal(val name: String, val target: String) : ParentHomeAction
    data object DismissError : ParentHomeAction
}
