package com.bancodopai.child.mvi.effect

sealed interface ChildHomeEffect {
    data object WithdrawalRequested : ChildHomeEffect
    data class ShowMessage(val message: String) : ChildHomeEffect
}
