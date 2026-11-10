package com.bancodopai.domain.model

import java.time.Instant

enum class TransactionType {
    DEPOSIT,
    WITHDRAW,
    INTEREST
}

enum class TransactionStatus {
    PENDING,
    APPROVED,
    DENIED
}

data class Transaction(
    val id: String,
    val childId: String,
    val type: TransactionType,
    val amountCents: Long,
    val description: String,
    val status: TransactionStatus,
    val timestamp: Instant
) {
    val isPending: Boolean
        get() = status == TransactionStatus.PENDING

    val isCredit: Boolean
        get() = type != TransactionType.WITHDRAW
}
