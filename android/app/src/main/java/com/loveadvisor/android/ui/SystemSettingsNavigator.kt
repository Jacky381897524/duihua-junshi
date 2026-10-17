package com.loveadvisor.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Standard Android routes only; OEMs may remove individual pages, so try progressively broader pages. */
class SystemSettingsNavigator(private val context: Context) {
    private val appUri get()=Uri.parse("package:${context.packageName}")
    fun overlay()=launch(listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,appUri),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,appUri),
        Intent(Settings.ACTION_SETTINGS)))
    fun accessibility()=launch(listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),Intent(Settings.ACTION_SETTINGS)))
    fun appDetails()=launch(listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,appUri),Intent(Settings.ACTION_SETTINGS)))
    private fun launch(routes: List<Intent>):Boolean {
        for(route in routes)try {
            context.startActivity(route.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_:ActivityNotFoundException) { }
        catch (_:SecurityException) { }
        return false
    }
}
