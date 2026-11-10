package com.bancodopai.parent.mvi.state

import com.bancodopai.core.toCentsOrNull
import com.bancodopai.domain.model.DepositReason
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.User

data class ParentHomeState(
    val isLoading: Boolean = true,
    val parent: User? = null,
    val child: User? = null,
    val transactions: List<Transaction> = emptyList(),
    val pendingWithdrawals: List<Transaction> = emptyList(),
    val errorMessage: String? = null,
    val isDepositSheetOpen: Boolean = false,
    val depositAmount: String = "",
    val depositReason: DepositReason = DepositReason.MESADA,
    val depositNote: String = "",
    val depositError: String? = null,
    val isSubmittingDeposit: Boolean = false,
    val processingTransactionId: String? = null,
    val interestRateInput: String = "",
    val isSavingInterestRate: Boolean = false,
    val isApplyingInterest: Boolean = false
) {
    val childBalanceCents: Long
        get() = child?.balanceCents ?: 0L

    val depositCents: Long?
        get() = depositAmount.toCentsOrNull()

    val canSubmitDeposit: Boolean
        get() = !isSubmittingDeposit &&
            (depositCents ?: 0L) > 0L &&
            (depositReason != DepositReason.OUTROS || depositNote.isNotBlank())
}
