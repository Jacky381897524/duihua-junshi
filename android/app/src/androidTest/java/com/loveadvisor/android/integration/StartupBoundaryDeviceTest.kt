package com.loveadvisor.android.integration

import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.loveadvisor.android.AdvisorApplication
import com.loveadvisor.android.AppStartupTrace
import com.loveadvisor.android.MainActivity
import com.loveadvisor.core.startup.StartupStage
import org.junit.Assert.*
import org.junit.Test

/** Requires a device; this does not reproduce an OEM installer's update placeholder. */
class StartupBoundaryDeviceTest {
    @Test fun launcherRecordsApplicationActivityAndDrawBoundariesWithoutIntentContents() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assertTrue(instrumentation.targetContext.applicationContext is AdvisorApplication)
        val intent=Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("startup-private-sentinel","never-store-this-intent-value")
        val activity=instrumentation.startActivitySync(intent)
        try {
            val deadline=SystemClock.elapsedRealtime()+5000
            while(SystemClock.elapsedRealtime()<deadline && AppStartupTrace.snapshot()?.current?.events?.any { it.stage==StartupStage.FIRST_DRAW }!=true) {
                instrumentation.waitForIdleSync();SystemClock.sleep(20)
            }
            val snapshot=AppStartupTrace.snapshot()!!
            assertEquals(Process.myPid(),snapshot.current.pid)
            val stages=snapshot.current.events.map { it.stage }
            assertTrue(stages.containsAll(listOf(StartupStage.PROCESS_ATTACHED,StartupStage.APPLICATION_CREATED,StartupStage.MAIN_CREATE_ENTERED,StartupStage.MAIN_CREATED,StartupStage.MAIN_RESUMED,StartupStage.FIRST_DRAW)))
            val text=Gson().toJson(snapshot)
            assertFalse(text.contains("startup-private-sentinel"));assertFalse(text.contains("never-store-this-intent-value"))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
