package com.bancodopai.child.mvi.action

sealed interface ChildHomeAction {
    data object OpenWithdrawSheet : ChildHomeAction
    data object CloseWithdrawSheet : ChildHomeAction
    data class WithdrawalAmountChanged(val value: String) : ChildHomeAction
    data class WithdrawalReasonChanged(val value: String) : ChildHomeAction
    data object SubmitWithdrawal : ChildHomeAction
    data object DismissError : ChildHomeAction
}
