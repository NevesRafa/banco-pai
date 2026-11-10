package com.bancodopai

import android.app.Application
import com.bancodopai.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class BancoDoPaiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@BancoDoPaiApp)
            modules(appModule)
        }
    }
}
