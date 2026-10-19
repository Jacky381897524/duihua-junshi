package com.loveadvisor.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.loveadvisor.core.startup.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Local startup only: no models, chat reads, permission changes or data migrations. */
object AppStartup {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor { Thread(it,"advisor-startup") }
    @Volatile private var loader:StartupLoader<AppGraph>?=null
    @Volatile private var stage="尚未启动"
    @Synchronized fun get(context:Context):StartupLoader<AppGraph> = loader ?: run {
        val app=context.applicationContext
        StartupLoader(worker,Executor { main.post(it) }) {
            val began=SystemClock.elapsedRealtime();stage="初始化本地组件"
            Log.i("AdvisorStartup","begin")
            val graph=AppGraph.get(app)
            stage="读取本地加密设置";graph.settings.load()
            stage="本地设置就绪"
            graph.record("startup",SystemClock.elapsedRealtime()-began,null,null)
            Log.i("AdvisorStartup","ready ${SystemClock.elapsedRealtime()-began}ms")
            graph
        }.also { created ->
            loader=created
            created.observe { state ->
                if(state.phase==StartupPhase.LOADING)main.postDelayed({ created.markSlow(state.attempt) },8000)
                if(state.phase==StartupPhase.FAILED)Log.w("AdvisorStartup","failed ${state.errorType}")
            }
        }
    }
    fun start(context:Context)=get(context).start()
    fun description(context:Context)="${get(context).state.phase} · $stage"+get(context).state.errorType?.let { " · $it" }.orEmpty()
}
