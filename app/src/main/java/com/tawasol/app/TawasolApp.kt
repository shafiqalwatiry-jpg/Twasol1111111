package com.tawasol.app

import android.app.Application
import com.tawasol.app.core.di.AppContainer
import com.tawasol.app.core.di.DefaultAppContainer

class TawasolApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
