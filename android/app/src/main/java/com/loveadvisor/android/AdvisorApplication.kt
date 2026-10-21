package com.loveadvisor.android

import android.app.Application
import android.content.Context
import com.loveadvisor.core.startup.StartupStage

/** Only startup metadata; providers and settings are not initialized here. */
class AdvisorApplication:Application() {
    override fun attachBaseContext(base:Context) {
        super.attachBaseContext(base)
        com.loveadvisor.android.diagnostics.DiagnosticLogs.attach(base)
        AppStartupTrace.attach(base)
    }
    override fun onCreate() {
        super.onCreate()
        com.loveadvisor.android.diagnostics.DiagnosticLogs.event(com.loveadvisor.core.diagnostics.LogStep.APP_CREATE,com.loveadvisor.core.diagnostics.LogState.OK)
        AppStartupTrace.mark(StartupStage.APPLICATION_CREATED)
    }
}
