package com.bancodopai.parent.mvi.effect

sealed interface ParentHomeEffect {
    data object DepositCompleted : ParentHomeEffect
    data object WithdrawalApproved : ParentHomeEffect
    data object WithdrawalDenied : ParentHomeEffect
    data class ShowMessage(val message: String) : ParentHomeEffect
}
