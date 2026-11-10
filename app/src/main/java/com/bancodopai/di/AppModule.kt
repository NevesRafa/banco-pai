package com.bancodopai.di

import com.bancodopai.data.remote.FirebaseFinanceRepository
import com.bancodopai.domain.repository.FinanceRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val IoDispatcher = named("io_dispatcher")

val appModule = module {

    // ─────────── Firebase ───────────
    single { FirebaseApp.getInstance() }
    single<FirebaseAuth> { FirebaseAuth.getInstance(get<FirebaseApp>()) }
    single<FirebaseFirestore> { FirebaseFirestore.getInstance(get<FirebaseApp>()) }

    // ─────────── Dispatchers ───────────
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }

    // ─────────── Repositórios ───────────
    single<FinanceRepository> {
        FirebaseFinanceRepository(
            auth = get(),
            firestore = get(),
            ioDispatcher = get<CoroutineDispatcher>(IoDispatcher)
        )
    }
}
