package com.bancodopai.child.mvi.state

import com.bancodopai.core.toCentsOrNull
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.User

data class ChildHomeState(
    val isLoading: Boolean = true,
    val child: User? = null,
    val transactions: List<Transaction> = emptyList(),
    val pendingWithdrawals: List<Transaction> = emptyList(),
    val weeklyInterestCents: Long = 0L,
    val errorMessage: String? = null,
    val isWithdrawSheetOpen: Boolean = false,
    val withdrawalAmount: String = "",
    val withdrawalReason: String = "",
    val withdrawalError: String? = null,
    val isSubmittingWithdrawal: Boolean = false,
    val isGoalSheetOpen: Boolean = false,
    val goalError: String? = null,
    val isSavingGoal: Boolean = false
) {
    val balanceCents: Long
        get() = child?.balanceCents ?: 0L

    val goalProgress: Float
        get() = child?.goalProgress ?: 0f

    val withdrawalCents: Long?
        get() = withdrawalAmount.toCentsOrNull()

    val canSubmitWithdrawal: Boolean
        get() = !isSubmittingWithdrawal &&
            (withdrawalCents ?: 0L) > 0L &&
            withdrawalReason.isNotBlank()
}
