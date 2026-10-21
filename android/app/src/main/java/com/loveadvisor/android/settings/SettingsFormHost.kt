package com.loveadvisor.android.settings

import android.os.Handler
import android.os.Looper
import com.loveadvisor.android.AppGraph
import com.loveadvisor.core.api.Cancellation
import java.util.concurrent.Executors
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*

interface SettingsFormHost {
    fun choose(title:String,items:List<String>,selected:Int,onSelected:(Int)->Unit)
    fun <T> work(id:String,task:(Cancellation)->T,onSuccess:(T)->Unit,onFailure:(String)->Unit)
    fun message(text:String)
    fun close()
}

class SettingsWorkScope(private val graph:AppGraph) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newFixedThreadPool(2)
    private val tasks=mutableMapOf<String,Cancellation>()
    private val logs=mutableMapOf<String,LogOperation?>()
    private var active=true
    fun cancel(id:String) { tasks.remove(id)?.cancel();Logs.end(logs.remove(id),LogState.CANCELLED,LogReason.USER_CANCELLED) }
    fun <T> work(id:String,task:(Cancellation)->T,success:(T)->Unit,failure:(String)->Unit) {
        if(!active) { Logs.event(Logs.step(id),LogState.SKIP,LogReason.RESULT_STALE);return }
        cancel(id)
        val cancel=Cancellation();tasks[id]=cancel
        val log=Logs.begin(Logs.step(id));logs[id]=log
        worker.execute {
            try {
                val result=Logs.within(log) { task(cancel) };cancel.check()
                main.post {
                    if(active && !cancel.cancelled && tasks[id]===cancel) {
                        tasks.remove(id);logs.remove(id)
                        try { Logs.within(log) { success(result) };if(result is com.loveadvisor.core.api.CatalogResult && result.error!=null)Logs.end(log,LogState.FAIL,LogReason.INVALID_RESPONSE) else Logs.end(log,LogState.OK) } catch(error:Exception) { val fault=LogFault.capture(error);Logs.end(log,LogState.FAIL,fault.reason,fault);throw error }
                    } else Logs.end(log,LogState.CANCELLED,LogReason.RESULT_STALE)
                }
            } catch(_:java.util.concurrent.CancellationException) { Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED) }
            catch(error:Exception) { val fault=LogFault.capture(error);Logs.end(log,LogState.FAIL,fault.reason,fault);val message=graph.safeError(error);main.post { if(active && !cancel.cancelled && tasks[id]===cancel) { tasks.remove(id);logs.remove(id);failure(message) } } }
        }
    }
    fun close() { active=false;tasks.keys.toList().forEach(::cancel);worker.shutdownNow() }
}
