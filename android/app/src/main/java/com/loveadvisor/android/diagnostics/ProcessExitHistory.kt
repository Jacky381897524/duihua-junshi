package com.loveadvisor.android.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.text.format.DateFormat
import com.loveadvisor.core.diagnostics.ProcessExitRecord
import com.loveadvisor.core.diagnostics.ProcessExitSummary

/** Call on a worker. Read only own-package OS metadata; never descriptions or trace streams. */
object ProcessExitHistory {
    fun read(context:Context):String {
        val manager=context.getSystemService(ActivityManager::class.java)
            ?: return "系统退出记录暂不可用；不能据此判断没有发生故障。"
        val own=context.packageName
        val records=manager.getHistoricalProcessExitReasons(own,0,5)
            .filter { it.processName==own || it.processName.orEmpty().startsWith("$own:") }
            .map { ProcessExitRecord(it.timestamp,it.pid,it.reason,it.status) }
        return ProcessExitSummary.describe(records) { DateFormat.format("yyyy-MM-dd HH:mm:ss",it).toString() }
    }
}
