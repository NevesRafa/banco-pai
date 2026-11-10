package com.bancodopai.parent.mvi.state

import com.bancodopai.core.toCentsOrNull
import com.bancodopai.domain.model.DepositReason
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.TransactionStatus
import com.bancodopai.domain.model.TransactionType
import com.bancodopai.domain.model.User

data class ParentHomeState(
    val isLoading: Boolean = true,
    val parent: User? = null,
    val children: List<User> = emptyList(),
    val selectedChildId: String? = null,
    val familyTransactions: List<Transaction> = emptyList(),
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
    /** Filho em foco: o selecionado ou, na falta dele, o primeiro. */
    val child: User?
        get() = children.firstOrNull { it.uid == selectedChildId } ?: children.firstOrNull()

    val transactions: List<Transaction>
        get() = familyTransactions.filter { it.childId == child?.uid }

    val pendingWithdrawals: List<Transaction>
        get() = transactions.filter {
            it.type == TransactionType.WITHDRAW &&
                it.status == TransactionStatus.PENDING
        }

    fun pendingCountFor(childId: String): Int =
        familyTransactions.count {
            it.childId == childId &&
                it.type == TransactionType.WITHDRAW &&
                it.status == TransactionStatus.PENDING
        }

    val childBalanceCents: Long
        get() = child?.balanceCents ?: 0L

    val depositCents: Long?
        get() = depositAmount.toCentsOrNull()

    val canSubmitDeposit: Boolean
        get() = !isSubmittingDeposit &&
            (depositCents ?: 0L) > 0L &&
            (depositReason != DepositReason.OUTROS || depositNote.isNotBlank())
}
