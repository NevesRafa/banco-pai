package com.bancodopai.data.remote

import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.TransactionStatus
import com.bancodopai.domain.model.TransactionType
import com.bancodopai.domain.model.User
import com.bancodopai.domain.model.UserRole
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.time.Instant

internal fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null

    val role = getString(FirestoreContract.ROLE)
        ?.let { raw -> UserRole.entries.firstOrNull { it.name == raw } }
        ?: return null

    return User(
        uid = id,
        name = getString(FirestoreContract.NAME).orEmpty(),
        email = getString(FirestoreContract.EMAIL).orEmpty(),
        role = role,
        balanceCents = longOrZero(FirestoreContract.BALANCE_CENTS),
        goalName = getString(FirestoreContract.GOAL_NAME).orEmpty(),
        goalTargetCents = longOrZero(FirestoreContract.GOAL_TARGET_CENTS),
        parentId = getString(FirestoreContract.PARENT_ID),
        familyIds = familyIds(),
        weeklyInterestRatePercent = (get(FirestoreContract.WEEKLY_INTEREST_RATE) as? Number)
            ?.toDouble() ?: 0.0,
        lastInterestAppliedAt = getTimestamp(FirestoreContract.LAST_INTEREST_APPLIED_AT)
            ?.toInstant(),
        inviteCode = getString(FirestoreContract.INVITE_CODE)
    )
}

internal fun DocumentSnapshot.toTransaction(): Transaction? {
    if (!exists()) return null

    val type = getString(FirestoreContract.TYPE)
        .toEnumOrNull<TransactionType>()
        ?: return null

    val status = getString(FirestoreContract.STATUS)
        .toEnumOrNull<TransactionStatus>()
        ?: return null

    val childId = getString(FirestoreContract.CHILD_ID) ?: return null
    val amountCents = longOrZero(FirestoreContract.AMOUNT_CENTS)

    if (amountCents <= 0L) return null

    return Transaction(
        id = id,
        childId = childId,
        type = type,
        amountCents = amountCents,
        description = getString(FirestoreContract.DESCRIPTION).orEmpty(),
        status = status,
        timestamp = getTimestamp(FirestoreContract.TIMESTAMP)?.toInstant() ?: Instant.EPOCH
    )
}

internal fun DocumentSnapshot.longOrZero(field: String): Long =
    (get(field) as? Number)?.toLong() ?: 0L

/**
 * Pais que administram este filho. Documentos antigos só têm parentId,
 * por isso ele é usado como fallback.
 */
internal fun DocumentSnapshot.familyIds(): List<String> {
    val ids = (get(FirestoreContract.PARENT_IDS) as? List<*>)
        ?.filterIsInstance<String>()
        .orEmpty()
    return ids.ifEmpty { listOfNotNull(getString(FirestoreContract.PARENT_ID)) }
}

internal fun Transaction.toDocument(
    parentIds: List<String>
): Map<String, Any> = buildMap {
    put(FirestoreContract.CHILD_ID, childId)
    put(FirestoreContract.TYPE, type.name)
    put(FirestoreContract.AMOUNT_CENTS, amountCents)
    put(FirestoreContract.DESCRIPTION, description)
    put(FirestoreContract.STATUS, status.name)
    put(FirestoreContract.TIMESTAMP, FieldValue.serverTimestamp())
    put(FirestoreContract.PARENT_IDS, parentIds)
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? {
    val raw = this ?: return null
    return enumValues<T>().firstOrNull { it.name == raw }
}
