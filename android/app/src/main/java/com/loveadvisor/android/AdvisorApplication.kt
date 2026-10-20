package com.loveadvisor.android

import android.app.Application
import android.content.Context
import com.loveadvisor.core.startup.StartupStage

/** Only startup metadata; providers and settings are not initialized here. */
class AdvisorApplication:Application() {
    override fun attachBaseContext(base:Context) {
        super.attachBaseContext(base)
        AppStartupTrace.attach(base)
    }
    override fun onCreate() {
        super.onCreate()
        AppStartupTrace.mark(StartupStage.APPLICATION_CREATED)
    }
}
