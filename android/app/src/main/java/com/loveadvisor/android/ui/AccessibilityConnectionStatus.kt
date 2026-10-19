package com.loveadvisor.android.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.loveadvisor.android.capture.ChatCaptureService

object AccessibilityConnectionStatus {
    fun label(context:Context):String {
        if(ChatCaptureService.instance!=null)return "已连接"
        val own=ComponentName(context,ChatCaptureService::class.java)
        val enabled=runCatching {
            Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it)==own }
        }.getOrNull() ?: return "连接状态暂不可用"
        return if(enabled)"已开启，等待系统连接" else "未开启"
    }
}
