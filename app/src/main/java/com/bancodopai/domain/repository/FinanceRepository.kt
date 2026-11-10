package com.bancodopai.domain.repository

import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.User
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {

    val currentUserId: String?

    val authState: Flow<String?>

    suspend fun signIn(email: String, password: String): Result<User>

    suspend fun signOut(): Result<Unit>

    fun observeUser(uid: String): Flow<User?>

    fun observeChildOfParent(parentId: String): Flow<User?>

    fun observeTransactions(childId: String): Flow<List<Transaction>>

    fun observePendingWithdrawals(childId: String): Flow<List<Transaction>>

    suspend fun deposit(
        childId: String,
        amountCents: Long,
        description: String
    ): Result<Transaction>

    suspend fun requestWithdrawal(
        childId: String,
        amountCents: Long,
        description: String
    ): Result<Transaction>

    suspend fun applyInterest(
        childId: String,
        amountCents: Long,
        description: String
    ): Result<Transaction>

    suspend fun approveWithdrawal(transactionId: String): Result<Transaction>

    suspend fun denyWithdrawal(transactionId: String): Result<Transaction>

    suspend fun updateGoal(
        childId: String,
        goalName: String,
        goalTargetCents: Long
    ): Result<Unit>
}
