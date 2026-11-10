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
    data object DismissError : ParentHomeAction
}
