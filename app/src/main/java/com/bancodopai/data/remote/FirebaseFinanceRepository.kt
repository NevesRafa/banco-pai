package com.bancodopai.data.remote

import com.bancodopai.core.firebaseResult
import com.bancodopai.core.toAppError
import com.bancodopai.domain.model.AppError
import com.bancodopai.domain.model.GoogleSignInResult
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.TransactionStatus
import com.bancodopai.domain.model.TransactionType
import com.bancodopai.domain.model.User
import com.bancodopai.domain.model.UserRole
import com.bancodopai.domain.repository.FinanceRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

private const val MAX_CATCH_UP_WEEKS = 4L
private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private val inviteRandom = SecureRandom()

class FirebaseFinanceRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val ioDispatcher: CoroutineDispatcher
) : FinanceRepository {

    private val users = firestore.collection(FirestoreContract.USERS)
    private val transactions = firestore.collection(FirestoreContract.TRANSACTIONS)
    private val invites = firestore.collection(FirestoreContract.INVITES)

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override val currentUserName: String?
        get() = auth.currentUser?.displayName

    override val currentUserEmail: String?
        get() = auth.currentUser?.email

    override val authState: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    // ───────────────────────── Autenticação ─────────────────────────

    override suspend fun signIn(
        email: String,
        password: String
    ): Result<User> = withContext(ioDispatcher) {
        firebaseResult {
            if (email.isBlank() || password.isBlank()) {
                throw AppError.Validation("Informe e-mail e senha.")
            }

            val firebaseUser = auth
                .signInWithEmailAndPassword(email.trim(), password)
                .await()
                .user
                ?: throw AppError.Unauthenticated

            val profile = users
                .document(firebaseUser.uid)
                .get()
                .await()
                .toUser()

            if (profile == null) {
                auth.signOut()
                throw AppError.NotFound
            }

            profile
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String
    ): Result<GoogleSignInResult> = withContext(ioDispatcher) {
        firebaseResult {
            if (idToken.isBlank()) {
                throw AppError.Validation("Token do Google inválido.")
            }

            val firebaseUser = auth
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
                .user
                ?: throw AppError.Unauthenticated

            val profile = users
                .document(firebaseUser.uid)
                .get()
                .await()
                .toUser()

            if (profile != null) {
                GoogleSignInResult.Existing(profile)
            } else {
                GoogleSignInResult.NeedsProfile(
                    suggestedName = firebaseUser.displayName.orEmpty(),
                    email = firebaseUser.email.orEmpty()
                )
            }
        }
    }

    override suspend fun createProfile(
        name: String,
        role: UserRole,
        inviteCode: String?
    ): Result<User> = withContext(ioDispatcher) {
        firebaseResult {
            val firebaseUser = auth.currentUser ?: throw AppError.Unauthenticated
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                throw AppError.Validation("Informe seu nome.")
            }

            var parentId: String? = null
            var code: String? = null

            if (role == UserRole.CHILD) {
                val normalized = inviteCode.orEmpty().trim().uppercase()
                if (normalized.isBlank()) {
                    throw AppError.Validation("Informe o código do seu pai.")
                }
                val invite = invites.document(normalized).get().await()
                parentId = invite.getString(FirestoreContract.PARENT_ID)
                    ?: throw AppError.Validation("Código de convite inválido.")
                code = normalized
            }

            val profile = User(
                uid = firebaseUser.uid,
                name = trimmedName,
                email = firebaseUser.email.orEmpty(),
                role = role,
                parentId = parentId,
                inviteCode = code
            )

            users.document(profile.uid).set(
                mapOf(
                    FirestoreContract.NAME to profile.name,
                    FirestoreContract.EMAIL to profile.email,
                    FirestoreContract.ROLE to profile.role.name,
                    FirestoreContract.BALANCE_CENTS to 0L,
                    FirestoreContract.GOAL_NAME to "",
                    FirestoreContract.GOAL_TARGET_CENTS to 0L,
                    FirestoreContract.WEEKLY_INTEREST_RATE to 0.0,
                    FirestoreContract.PARENT_ID to parentId,
                    FirestoreContract.INVITE_CODE to code
                )
            ).await()

            if (role == UserRole.PARENT) {
                ensureInviteCode(profile.uid).getOrThrow()
            }

            profile
        }
    }

    override suspend fun signOut(): Result<Unit> =
        withContext(ioDispatcher) {
            firebaseResult { auth.signOut() }
        }

    // ───────────────────── Observações em tempo real ─────────────────────

    override fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val registration = users.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toAppError())
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUser())
            }

        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override fun observeChildOfParent(parentId: String): Flow<User?> = callbackFlow {
        val registration = users
            .whereEqualTo(FirestoreContract.PARENT_ID, parentId)
            .whereEqualTo(FirestoreContract.ROLE, UserRole.CHILD.name)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toAppError())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.firstOrNull()?.toUser())
            }

        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override fun observeTransactions(
        childId: String,
        parentId: String?
    ): Flow<List<Transaction>> = callbackFlow {
        // A regra do Firestore precisa que a query filtre por um campo
        // diretamente comparável ao UID (childId ou parentId).
        val query = if (parentId == null) {
            transactions.whereEqualTo(FirestoreContract.CHILD_ID, childId)
        } else {
            transactions.whereEqualTo(FirestoreContract.PARENT_ID, parentId)
        }

        val registration = query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toAppError())
                    return@addSnapshotListener
                }

                val items = snapshot?.documents
                    ?.mapNotNull { it.toTransaction() }
                    ?.sortedByDescending { it.timestamp }
                    .orEmpty()

                trySend(items)
            }

        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    override fun observePendingWithdrawals(
        childId: String,
        parentId: String?
    ): Flow<List<Transaction>> =
        observeTransactions(childId, parentId)
            .map { list ->
                list.filter {
                    it.type == TransactionType.WITHDRAW &&
                        it.status == TransactionStatus.PENDING
                }
            }
            .distinctUntilChanged()

    // ───────────────────────── Escritas ─────────────────────────

    override suspend fun deposit(
        childId: String,
        amountCents: Long,
        description: String
    ): Result<Transaction> = credit(
        childId = childId,
        type = TransactionType.DEPOSIT,
        amountCents = amountCents,
        description = description
    )

    override suspend fun applyInterest(
        childId: String,
        amountCents: Long,
        description: String
    ): Result<Transaction> = credit(
        childId = childId,
        type = TransactionType.INTEREST,
        amountCents = amountCents,
        description = description
    )

    private suspend fun credit(
        childId: String,
        type: TransactionType,
        amountCents: Long,
        description: String
    ): Result<Transaction> = withContext(ioDispatcher) {
        firebaseResult {
            validateAmount(amountCents)
            validateDescription(description)

            val childRef = users.document(childId)
            val transactionRef = transactions.document()

            val newTransaction = Transaction(
                id = transactionRef.id,
                childId = childId,
                type = type,
                amountCents = amountCents,
                description = description.trim(),
                status = TransactionStatus.APPROVED,
                timestamp = Instant.now()
            )

            firestore.runTransaction { firestoreTransaction ->
                val childSnapshot = firestoreTransaction.get(childRef)
                if (!childSnapshot.exists()) throw AppError.NotFound

                firestoreTransaction.set(
                    transactionRef,
                    newTransaction.toDocument(
                        childSnapshot.getString(FirestoreContract.PARENT_ID)
                    )
                )
                firestoreTransaction.update(
                    childRef,
                    FirestoreContract.BALANCE_CENTS,
                    FieldValue.increment(amountCents)
                )
                Unit
            }.await()

            newTransaction
        }
    }

    override suspend fun requestWithdrawal(
        childId: String,
        amountCents: Long,
        description: String
    ): Result<Transaction> = withContext(ioDispatcher) {
        firebaseResult {
            validateAmount(amountCents)
            validateDescription(description)

            val childRef = users.document(childId)
            val transactionRef = transactions.document()

            val request = Transaction(
                id = transactionRef.id,
                childId = childId,
                type = TransactionType.WITHDRAW,
                amountCents = amountCents,
                description = description.trim(),
                status = TransactionStatus.PENDING,
                timestamp = Instant.now()
            )

            firestore.runTransaction { firestoreTransaction ->
                val childSnapshot = firestoreTransaction.get(childRef)
                if (!childSnapshot.exists()) throw AppError.NotFound

                val available = childSnapshot.longOrZero(
                    FirestoreContract.BALANCE_CENTS
                )

                if (amountCents > available) {
                    throw AppError.InsufficientFunds(available)
                }

                firestoreTransaction.set(
                    transactionRef,
                    request.toDocument(
                        childSnapshot.getString(FirestoreContract.PARENT_ID)
                    )
                )
                Unit
            }.await()

            request
        }
    }

    override suspend fun approveWithdrawal(
        transactionId: String
    ): Result<Transaction> = withContext(ioDispatcher) {
        firebaseResult {
            val transactionRef = transactions.document(transactionId)

            firestore.runTransaction { firestoreTransaction ->
                val transaction = firestoreTransaction
                    .get(transactionRef)
                    .toTransaction()
                    ?: throw AppError.NotFound

                ensurePendingWithdrawal(transaction)

                val childRef = users.document(transaction.childId)
                val childSnapshot = firestoreTransaction.get(childRef)
                if (!childSnapshot.exists()) throw AppError.NotFound

                val balance = childSnapshot.longOrZero(
                    FirestoreContract.BALANCE_CENTS
                )

                if (balance < transaction.amountCents) {
                    throw AppError.InsufficientFunds(balance)
                }

                firestoreTransaction.update(
                    childRef,
                    FirestoreContract.BALANCE_CENTS,
                    balance - transaction.amountCents
                )
                firestoreTransaction.update(
                    transactionRef,
                    FirestoreContract.STATUS,
                    TransactionStatus.APPROVED.name
                )

                transaction.copy(status = TransactionStatus.APPROVED)
            }.await()
        }
    }

    override suspend fun denyWithdrawal(
        transactionId: String
    ): Result<Transaction> = withContext(ioDispatcher) {
        firebaseResult {
            val transactionRef = transactions.document(transactionId)

            firestore.runTransaction { firestoreTransaction ->
                val transaction = firestoreTransaction
                    .get(transactionRef)
                    .toTransaction()
                    ?: throw AppError.NotFound

                ensurePendingWithdrawal(transaction)

                firestoreTransaction.update(
                    transactionRef,
                    FirestoreContract.STATUS,
                    TransactionStatus.DENIED.name
                )

                transaction.copy(status = TransactionStatus.DENIED)
            }.await()
        }
    }

    override suspend fun updateGoal(
        childId: String,
        goalName: String,
        goalTargetCents: Long
    ): Result<Unit> = withContext(ioDispatcher) {
        firebaseResult {
            if (goalName.isBlank()) {
                throw AppError.Validation("Dê um nome para a meta.")
            }
            if (goalTargetCents <= 0L) {
                throw AppError.Validation("A meta precisa ter um valor maior que zero.")
            }

            users.document(childId).update(
                mapOf(
                    FirestoreContract.GOAL_NAME to goalName.trim(),
                    FirestoreContract.GOAL_TARGET_CENTS to goalTargetCents
                )
            ).await()

            Unit
        }
    }

    override suspend fun updateInterestRate(
        parentId: String,
        childId: String?,
        percentPerWeek: Double
    ): Result<Unit> = withContext(ioDispatcher) {
        firebaseResult {
            val percent = percentPerWeek.coerceIn(0.0, 100.0)
            val parentRef = users.document(parentId)

            firestore.runTransaction { firestoreTransaction ->
                val parentSnapshot = firestoreTransaction.get(parentRef)
                if (!parentSnapshot.exists()) throw AppError.NotFound

                val updates = mutableMapOf<String, Any>(
                    FirestoreContract.WEEKLY_INTEREST_RATE to percent
                )
                if (parentSnapshot.getTimestamp(
                        FirestoreContract.LAST_INTEREST_APPLIED_AT
                    ) == null
                ) {
                    updates[FirestoreContract.LAST_INTEREST_APPLIED_AT] =
                        FieldValue.serverTimestamp()
                }

                firestoreTransaction.update(parentRef, updates)
                if (childId != null) {
                    firestoreTransaction.update(
                        users.document(childId),
                        FirestoreContract.WEEKLY_INTEREST_RATE,
                        percent
                    )
                }
                Unit
            }.await()
        }
    }

    override suspend fun applyDueInterest(
        parentId: String,
        childId: String
    ): Result<Transaction?> = withContext(ioDispatcher) {
        firebaseResult {
            applyInterestInternal(parentId, childId, manual = false)
        }
    }

    override suspend fun applyInterestNow(
        parentId: String,
        childId: String
    ): Result<Transaction?> = withContext(ioDispatcher) {
        firebaseResult {
            applyInterestInternal(parentId, childId, manual = true)
        }
    }

    override suspend fun ensureInviteCode(
        parentId: String
    ): Result<String> = withContext(ioDispatcher) {
        firebaseResult {
            val parentRef = users.document(parentId)
            val parentSnapshot = parentRef.get().await()
            if (!parentSnapshot.exists()) throw AppError.NotFound

            val parentName = parentSnapshot
                .getString(FirestoreContract.NAME)
                .orEmpty()

            val existing = parentSnapshot
                .getString(FirestoreContract.INVITE_CODE)
                ?.takeIf { it.isNotBlank() }

            if (existing != null) {
                invites.document(existing).set(
                    mapOf(
                        FirestoreContract.PARENT_ID to parentId,
                        FirestoreContract.PARENT_NAME to parentName,
                        FirestoreContract.TIMESTAMP to FieldValue.serverTimestamp()
                    )
                ).await()
                return@firebaseResult existing
            }

            val code = generateInviteCode()
            val batch = firestore.batch()
            batch.set(
                invites.document(code),
                mapOf(
                    FirestoreContract.PARENT_ID to parentId,
                    FirestoreContract.PARENT_NAME to parentName,
                    FirestoreContract.TIMESTAMP to FieldValue.serverTimestamp()
                )
            )
            batch.update(parentRef, FirestoreContract.INVITE_CODE, code)
            batch.commit().await()
            code
        }
    }

    override suspend fun resolveInviteCode(
        code: String
    ): Result<String> = withContext(ioDispatcher) {
        firebaseResult {
            val normalized = code.trim().uppercase()
            if (normalized.isBlank()) {
                throw AppError.Validation("Informe o código do seu pai.")
            }
            invites.document(normalized).get().await()
                .getString(FirestoreContract.PARENT_ID)
                ?: throw AppError.Validation("Código de convite inválido.")
        }
    }

    // ───────────────────────── Helpers ─────────────────────────

    private suspend fun applyInterestInternal(
        parentId: String,
        childId: String,
        manual: Boolean
    ): Transaction? {
        val parentRef = users.document(parentId)
        val childRef = users.document(childId)
        val transactionRef = transactions.document()
        val now = Instant.now()

        return firestore.runTransaction<Transaction?> { firestoreTransaction ->
            val parentSnapshot = firestoreTransaction.get(parentRef)
            val childSnapshot = firestoreTransaction.get(childRef)
            if (!parentSnapshot.exists() || !childSnapshot.exists()) {
                throw AppError.NotFound
            }

            val ratePercent = (parentSnapshot.get(FirestoreContract.WEEKLY_INTEREST_RATE) as? Number)
                ?.toDouble() ?: 0.0
            if (ratePercent <= 0.0) {
                if (manual) throw AppError.Validation("Defina a taxa de rendimento primeiro.")
                return@runTransaction null
            }

            val lastApplied = parentSnapshot
                .getTimestamp(FirestoreContract.LAST_INTEREST_APPLIED_AT)
                ?.toDate()
                ?.toInstant()

            val weeks: Long
            val newLastApplied: Instant

            if (manual) {
                weeks = 1L
                newLastApplied = now
            } else {
                if (lastApplied == null) {
                    firestoreTransaction.update(
                        parentRef,
                        FirestoreContract.LAST_INTEREST_APPLIED_AT,
                        FieldValue.serverTimestamp()
                    )
                    return@runTransaction null
                }

                val elapsedWeeks = ChronoUnit.DAYS.between(lastApplied, now) / 7
                if (elapsedWeeks < 1) return@runTransaction null

                weeks = elapsedWeeks.coerceAtMost(MAX_CATCH_UP_WEEKS)
                newLastApplied = lastApplied.plus(weeks * 7, ChronoUnit.DAYS)
            }

            val balance = childSnapshot.longOrZero(FirestoreContract.BALANCE_CENTS)
            val totalCents = (balance * ratePercent / 100.0).roundToLong() * weeks

            firestoreTransaction.update(
                parentRef,
                FirestoreContract.LAST_INTEREST_APPLIED_AT,
                Timestamp(Date.from(newLastApplied))
            )

            if (totalCents <= 0L) return@runTransaction null

            val description = if (weeks == 1L) {
                "Rendimento de 1 semana"
            } else {
                "Rendimento de $weeks semanas"
            }

            val credit = Transaction(
                id = transactionRef.id,
                childId = childId,
                type = TransactionType.INTEREST,
                amountCents = totalCents,
                description = description,
                status = TransactionStatus.APPROVED,
                timestamp = now
            )

            firestoreTransaction.set(
                transactionRef,
                credit.toDocument(parentId)
            )
            firestoreTransaction.update(
                childRef,
                FirestoreContract.BALANCE_CENTS,
                FieldValue.increment(totalCents)
            )

            credit
        }.await()
    }

    private fun generateInviteCode(): String {
        val builder = StringBuilder(6)
        repeat(6) {
            builder.append(INVITE_ALPHABET[inviteRandom.nextInt(INVITE_ALPHABET.length)])
        }
        return builder.toString()
    }

    private fun validateAmount(amountCents: Long) {
        if (amountCents <= 0L) {
            throw AppError.Validation("O valor deve ser maior que zero.")
        }
    }

    private fun validateDescription(description: String) {
        if (description.isBlank()) {
            throw AppError.Validation("Descreva o motivo do lançamento.")
        }
    }

    private fun ensurePendingWithdrawal(transaction: Transaction) {
        if (transaction.type != TransactionType.WITHDRAW) {
            throw AppError.Validation("Esta transação não é um pedido de saque.")
        }
        if (transaction.status != TransactionStatus.PENDING) {
            throw AppError.Validation("Este pedido já foi processado.")
        }
    }
}
