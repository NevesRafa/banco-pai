package com.bancodopai.child.mvi.action

sealed interface ChildHomeAction {
    data object OpenWithdrawSheet : ChildHomeAction
    data object CloseWithdrawSheet : ChildHomeAction
    data class WithdrawalAmountChanged(val value: String) : ChildHomeAction
    data class WithdrawalReasonChanged(val value: String) : ChildHomeAction
    data object SubmitWithdrawal : ChildHomeAction
    data object DismissError : ChildHomeAction
    data object OpenGoalSheet : ChildHomeAction
    data object CloseGoalSheet : ChildHomeAction
    data class SubmitGoal(val name: String, val target: String) : ChildHomeAction
}
