package com.loveadvisor.android.advisor
import android.os.Handler
import android.os.Looper
import com.loveadvisor.android.AppGraph
import com.loveadvisor.core.api.*
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.memory.*
import com.loveadvisor.core.capture.WindowSnapshot
import java.util.concurrent.Executors
import java.util.UUID
class AdvisorController(private val graph:AppGraph) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var cancel:Cancellation?=null
    private data class Context(val snapshot:ChatSnapshot,val goal:String,val background:String,val objectId:String?,val replyTarget:ReplyTarget?)
    private var lastContext:Context?=null
    private val listeners=linkedSetOf<(BatchState)->Unit>()
    fun listen(listener:(BatchState)->Unit) { listeners.add(listener); listener(graph.coordinator.state) }
    fun unlisten(listener:(BatchState)->Unit) { listeners.remove(listener) }
    fun publish() { listeners.toList().forEach { it(graph.coordinator.state) } }
    fun analyze(snapshot:ChatSnapshot,goal:String=graph.currentGoal,background:String="",objectId:String?=graph.currentObjectId,replyTarget:ReplyTarget?=null) {
        val pending=graph.coordinator.begin(snapshot,replyTarget) ?: return
        lastContext=Context(snapshot,goal,background,objectId,replyTarget)
        cancel?.cancel(); val current=Cancellation(); cancel=current; publish()
        val started=System.currentTimeMillis()
        worker.execute {
            try {
                val input=graph.inputFor(snapshot,goal,background,objectId).copy(replyTarget=replyTarget)
                val batch=graph.generate(input,pending.analysis,pending.previous,current); current.check()
                main.post {
                    if(!current.cancelled && graph.coordinator.complete(pending.requestTicket,batch)) {
                        graph.record("advisor",System.currentTimeMillis()-started,null,null)
                        publish()
                        worker.execute {
                            try { graph.profiles.withExisting(objectId) { target ->
                                if(current.cancelled || !graph.tracker.isCurrent(pending.requestTicket.session)) return@withExisting
                                if(graph.settings.load().historyEnabled) graph.profiles.rememberChat(target.id,snapshot)

                            } } catch(_:Exception) { graph.record("memory",0,null,"failure") }
                        }
                    }
                }
            } catch(_:java.util.concurrent.CancellationException) { }
            catch(e:Exception) { graph.record("advisor",System.currentTimeMillis()-started,(e as? ApiException)?.status,"failure"); main.post { if(!current.cancelled) { graph.coordinator.fail(pending.requestTicket,graph.safeError(e)); publish() } } }
        }
    }
    fun refreshIndependent():Boolean { val context=lastContext ?: return false; if(context.snapshot.packageName!=graph.context.packageName || graph.tracker.lastSnapshot!=context.snapshot) return false; analyze(context.snapshot,context.goal,context.background,context.objectId,context.replyTarget); return true }
    fun observe(snapshot:ChatSnapshot,message:String?=null) {
        val old=graph.tracker.currentToken
        graph.coordinator.observe(snapshot,message)
        if(old!=graph.tracker.currentToken) { cancel?.cancel(); cancel=null }
        publish()
    }
    fun status(message:String,failure:Boolean=false) { graph.coordinator.status(message,failure); publish() }
    fun awaitCapture(message:String) { cancel?.cancel(); cancel=null; graph.coordinator.cancelPending(message); publish() }
    fun awaitVision(window:WindowSnapshot,message:String):Boolean {
        val retained=graph.coordinator.awaitVision(window,message)
        if(!retained) { cancel?.cancel(); cancel=null }
        publish(); return retained
    }
    fun pauseCapture(message:String) { cancel?.cancel(); cancel=null; graph.coordinator.pauseCapture(message); publish() }
    fun invalidate(reason:String="会话已变化") { cancel?.cancel(); cancel=null; graph.coordinator.invalidate(); graph.record("capture",0,null,reason); publish() }
}
