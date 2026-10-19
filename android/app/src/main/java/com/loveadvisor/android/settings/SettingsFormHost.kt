package com.loveadvisor.android.settings

import android.os.Handler
import android.os.Looper
import com.loveadvisor.android.AppGraph
import com.loveadvisor.core.api.Cancellation
import java.util.concurrent.Executors

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
    private var active=true
    fun <T> work(id:String,task:(Cancellation)->T,success:(T)->Unit,failure:(String)->Unit) {
        if(!active)return
        tasks.remove(id)?.cancel()
        val cancel=Cancellation();tasks[id]=cancel
        worker.execute {
            try {
                val result=task(cancel);cancel.check()
                main.post { if(active && !cancel.cancelled && tasks[id]===cancel) { tasks.remove(id);success(result) } }
            } catch(_:java.util.concurrent.CancellationException) {}
            catch(error:Exception) { val message=graph.safeError(error);main.post { if(active && !cancel.cancelled && tasks[id]===cancel) { tasks.remove(id);failure(message) } } }
        }
    }
    fun close() { active=false;tasks.values.forEach { it.cancel() };tasks.clear();worker.shutdownNow() }
}
