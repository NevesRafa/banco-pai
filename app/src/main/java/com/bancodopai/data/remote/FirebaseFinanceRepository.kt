package com.bancodopai.data.remote

import com.bancodopai.core.firebaseResult
import com.bancodopai.core.toAppError
import com.bancodopai.domain.model.AppError
import com.bancodopai.domain.model.Transaction
import com.bancodopai.domain.model.TransactionStatus
import com.bancodopai.domain.model.TransactionType
import com.bancodopai.domain.model.User
import com.bancodopai.domain.model.UserRole
import com.bancodopai.domain.repository.FinanceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant

class FirebaseFinanceRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val ioDispatcher: CoroutineDispatcher
) : FinanceRepository {

    private val users = firestore.collection(FirestoreContract.USERS)
    private val transactions = firestore.collection(FirestoreContract.TRANSACTIONS)

    override val currentUserId: String?
        get() = auth.currentUser?.uid

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

    // ───────────────────────── Helpers ─────────────────────────

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
