package com.loveadvisor.android.integration

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.ui.SystemSettingsNavigator
import org.junit.Assert.*
import org.junit.Test

class SystemSettingsNavigatorTest {
    @Test fun missingPerAppOverlayPageFallsBackToTheGeneralPermissionPage() {
        var opened:Intent?=null
        val context=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent:Intent) {
                if(intent.data!=null)throw ActivityNotFoundException()
                opened=intent
            }
        }
        assertTrue(SystemSettingsNavigator(context).overlay())
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,opened?.action)
        assertNull(opened?.data)
    }
    @Test fun blockedAccessibilityPageFallsBackToSystemSettings() {
        var opened:Intent?=null
        val context=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent:Intent) {
                if(intent.action==Settings.ACTION_ACCESSIBILITY_SETTINGS)throw SecurityException()
                opened=intent
            }
        }
        assertTrue(SystemSettingsNavigator(context).accessibility())
        assertEquals(Settings.ACTION_SETTINGS,opened?.action)
    }
    @Test fun noUsableSettingsActivityReportsFailureWithoutCrashing() {
        val context=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent:Intent) { throw ActivityNotFoundException() }
        }
        assertFalse(SystemSettingsNavigator(context).overlay())
        assertFalse(SystemSettingsNavigator(context).accessibility())
        assertFalse(SystemSettingsNavigator(context).appDetails())
    }
}
