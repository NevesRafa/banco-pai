package com.bancodopai.domain.repository

import com.bancodopai.domain.model.GoogleSignInResult
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.User
import com.bancodopai.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {

    val currentUserId: String?

    val currentUserName: String?

    val currentUserEmail: String?

    val authState: Flow<String?>

    suspend fun signIn(email: String, password: String): Result<User>

    suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInResult>

    suspend fun createProfile(
        name: String,
        role: UserRole,
        inviteCode: String?
    ): Result<User>

    suspend fun signOut(): Result<Unit>

    fun observeUser(uid: String): Flow<User?>

    fun observeChildrenOfParent(parentId: String): Flow<List<User>>

    fun observeTransactions(
        childId: String,
        parentId: String? = null
    ): Flow<List<Transaction>>

    /** Todas as transações dos filhos administrados pela família. */
    fun observeFamilyTransactions(parentId: String): Flow<List<Transaction>>

    fun observePendingWithdrawals(
        childId: String,
        parentId: String? = null
    ): Flow<List<Transaction>>

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

    suspend fun updateInterestRate(
        childId: String,
        percentPerWeek: Double
    ): Result<Unit>

    /**
     * Aplica o rendimento das semanas vencidas. A conferência das datas
     * acontece dentro da transação, então chamadas repetidas não duplicam.
     * Retorna null quando nada é devido.
     */
    suspend fun applyDueInterest(childId: String): Result<Transaction?>

    /**
     * Credita uma semana de rendimento agora e reinicia a contagem.
     */
    suspend fun applyInterestNow(childId: String): Result<Transaction?>

    suspend fun ensureInviteCode(parentId: String): Result<String>

    /** Registra os filhos do código da família, para que um novo responsável possa entrar. */
    suspend fun linkInviteToChildren(inviteCode: String, childIds: List<String>): Result<Unit>

    /** Copia os membros da família para transações antigas do filho. */
    suspend fun syncFamilyTransactions(childId: String, parentId: String): Result<Unit>

    suspend fun resolveInviteCode(code: String): Result<String>
}
