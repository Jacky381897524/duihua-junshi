package com.loveadvisor.android

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import com.loveadvisor.core.startup.StartupAttempt
import com.loveadvisor.core.startup.StartupStage
import com.loveadvisor.core.startup.StartupTrace
import com.loveadvisor.core.startup.StartupTraceSnapshot
import java.util.concurrent.Executors

/** Local bounded metadata; description/snapshot never resolve a path or read disk. */
object AppStartupTrace {
    private val worker=Executors.newSingleThreadExecutor { Thread(it,"advisor-startup-trace") }
    @Volatile private var trace:StartupTrace?=null

    @Synchronized fun attach(context:Context) {
        if(trace!=null)return
        trace=StartupTrace(
            { context.filesDir.resolve("startup-trace.json") },worker,BuildConfig.VERSION_NAME,
            Process.myPid(),System.currentTimeMillis(),SystemClock::elapsedRealtime
        )
        Log.i("AdvisorStartup","${BuildConfig.VERSION_NAME} pid=${Process.myPid()} PROCESS_ATTACHED")
    }

    fun mark(stage:StartupStage) {
        val current=trace ?: return
        if(current.mark(stage)) {
            val value=current.snapshot.current
            Log.i("AdvisorStartup","${value.version} pid=${value.pid} ${stage.name} +${value.events.first { it.stage==stage }.elapsedMs}ms")
        }
    }

    fun snapshot():StartupTraceSnapshot?=trace?.snapshot

    fun description():String {
        val value=snapshot() ?: return "尚未建立进程启动记录"
        fun attempt(heading:String,item:StartupAttempt)=buildString {
            append("$heading：${item.version} · PID ${item.pid} · ${DateFormat.format("MM-dd HH:mm:ss",item.startedAtMillis)}\n")
            append(item.events.joinToString(" → ") { "${label(it.stage)} +${it.elapsedMs}ms" })
        }
        return buildString {
            append(attempt("本次进程",value.current))
            append("\n\n")
            append(value.previous?.let { attempt("上次已记录进程",it) }
                ?: if(value.historyLoaded)"没有可用的上次进程记录" else "正在后台读取上次进程记录")
            if(value.persistenceAvailable==false)append("\n本次记录暂未写入，仅保留内存状态")
            append("\n每个阶段仅记录首次到达；磁盘异步保存，缺少记录不能单独证明卡死。首次绘制仅表示应用绘制回调。")
        }
    }

    private fun label(stage:StartupStage)=when(stage) {
        StartupStage.PROCESS_ATTACHED -> "进程已附加"
        StartupStage.APPLICATION_CREATED -> "应用已创建"
        StartupStage.MAIN_CREATE_ENTERED -> "进入主页创建"
        StartupStage.MAIN_CREATED -> "主页创建返回"
        StartupStage.MAIN_RESUMED -> "主页已恢复"
        StartupStage.FIRST_DRAW -> "首次绘制"
        StartupStage.GRAPH_BEGIN -> "开始初始化组件"
        StartupStage.GRAPH_READY -> "组件就绪"
        StartupStage.SETTINGS_READ_BEGIN -> "开始读取设置"
        StartupStage.SETTINGS_READ_READY -> "设置读取完成"
        StartupStage.STARTUP_READY -> "启动就绪"
        StartupStage.STARTUP_SLOW -> "读取较慢"
        StartupStage.STARTUP_FAILED -> "加载失败"
    }
}
