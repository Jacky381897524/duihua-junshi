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
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*
class AdvisorController(private val graph:AppGraph) {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var cancel:Cancellation?=null
    private var operation:LogOperation?=null
    private fun cancelOperation(reason:LogReason=LogReason.USER_CANCELLED) { cancel?.cancel();cancel=null;Logs.end(operation,LogState.CANCELLED,reason);operation=null }
    private data class Context(val snapshot:ChatSnapshot,val goal:String,val background:String,val objectId:String?,val replyTarget:ReplyTarget?)
    private var lastContext:Context?=null
    private val listeners=linkedSetOf<(BatchState)->Unit>()
    fun listen(listener:(BatchState)->Unit) { listeners.add(listener); listener(graph.coordinator.state) }
    fun unlisten(listener:(BatchState)->Unit) { listeners.remove(listener) }
    fun publish() { listeners.toList().forEach { it(graph.coordinator.state) } }
    fun analyze(snapshot:ChatSnapshot,goal:String=graph.currentGoal,background:String="",objectId:String?=graph.currentObjectId,replyTarget:ReplyTarget?=null,diagnosticParent:LogOperation?=null) {
        val pending=graph.coordinator.begin(snapshot,replyTarget) ?: run { Logs.event(LogStep.REPLY_REQUEST,LogState.SKIP,LogReason.BUSY);return }
        lastContext=Context(snapshot,goal,background,objectId,replyTarget)
        cancelOperation();val log=Logs.begin(LogStep.REPLY_REQUEST,diagnosticParent,metrics=mapOf(LogMetric.MESSAGE_COUNT to snapshot.messages.size.toLong(),LogMetric.CHAT_KIND to if(snapshot.identity?.kind==ChatKind.GROUP)2L else 1L));operation=log
        val current=Cancellation(); cancel=current; publish()
        val releaseLearning=graph.personalMemory.beginForeground()

        worker.execute {
            try {
                val batch=Logs.within(log) {
                    if(snapshot.source!=CaptureSource.PASTE)graph.personalMemory.observe(snapshot)
                    val currentRows=ConversationTimePolicy.currentMessages(snapshot)
                    Logs.event(LogStep.TIME_CONTEXT,LogState.OK,metrics=mapOf(LogMetric.MESSAGE_COUNT to snapshot.messages.size.toLong(),LogMetric.EXPIRED_MESSAGE_COUNT to (snapshot.messages.size-currentRows.size).toLong(),LogMetric.ENABLED to if(replyTarget==null)1L else 0L))
                    val input=graph.inputFor(snapshot,goal,background,objectId,replyTarget)
                    Logs.event(LogStep.REPLY_REQUEST,metrics=mapOf(LogMetric.ENABLED to if(input.realisticRepliesEnabled)2L else if(input.naturalRepliesEnabled)1L else 0L))
                    graph.generate(input,pending.analysis,pending.previous,current)
                }; current.check()
                main.post {
                    if(current.cancelled) { Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED);return@post }
                    when(graph.coordinator.completeResult(pending.requestTicket,batch)) {
                        BatchCompletion.ACCEPTED -> {
                            graph.replySessions.remember(SavedConversationReplies(snapshot,batch,replyTarget))
                            Logs.event(LogStep.REPLY_RESULT,LogState.OK,operation=log,metrics=mapOf(LogMetric.RESULT_COUNT to batch.replies.size.toLong()));Logs.end(log,LogState.OK)
                            val currentIds=ConversationTimePolicy.currentMessages(snapshot).map { it.id }.toSet()
                            if(snapshot.source==CaptureSource.PASTE)graph.coordinator.status(if(com.loveadvisor.core.capture.ChatApps.supported(snapshot.packageName))"单句分析 · 填入前核对当前聊天" else "单句分析 · 未确认对象，可复制回复；读取聊天后可验证填入")
                            else if(replyTarget!=null && replyTarget.messageId !in currentIds)graph.coordinator.status("正在回复历史消息 · 按当前时间重提话题")
                            else if(currentIds.size<snapshot.messages.size)graph.coordinator.status("已排除${snapshot.messages.size-currentIds.size}条旧消息 · 按近24小时话题判断")
                            publish()
                            worker.execute {
                                try { graph.profiles.withExisting(objectId) { target ->
                                    if(current.cancelled || !graph.tracker.isCurrent(pending.requestTicket.session)) return@withExisting
                                    if(graph.settings.load().historyEnabled) graph.profiles.rememberChat(target.id,snapshot)

                                } } catch(error:Exception) { Logs.failed(LogStep.LOCAL_TASK,error,log) }
                            }
                        }
                        BatchCompletion.REJECTED -> {
                            Logs.end(log,LogState.FAIL,LogReason.INVALID_RESPONSE)
                            publish()
                        }
                        BatchCompletion.STALE -> Logs.end(log,LogState.CANCELLED,LogReason.RESULT_STALE)
                    }
                }
            } catch(_:java.util.concurrent.CancellationException) { Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED) }
            catch(e:Exception) { val fault=LogFault.capture(e);Logs.end(log,LogState.FAIL,fault.reason,fault);main.post { if(!current.cancelled) { graph.coordinator.fail(pending.requestTicket,graph.safeError(e)); publish() } } }
            finally { releaseLearning() }
        }
    }
    fun refreshIndependent():Boolean { val context=lastContext ?: return false; if(context.snapshot.packageName!=graph.context.packageName || graph.tracker.lastSnapshot!=context.snapshot) return false; analyze(context.snapshot,context.goal,context.background,context.objectId,context.replyTarget); return true }
    fun observe(snapshot:ChatSnapshot,message:String?=null) {
        val old=graph.tracker.currentToken
        graph.coordinator.observe(snapshot,message)
        if(old!=graph.tracker.currentToken)cancelOperation(LogReason.CONTEXT_CHANGED)
        publish()
    }
    fun status(message:String,failure:Boolean=false) { if(failure)Logs.event(LogStep.READ_FINISH,LogState.FAIL,Logs.reason(message));graph.coordinator.status(message,failure); publish() }
    fun awaitCapture(message:String) { cancelOperation();graph.coordinator.cancelPending(message); publish() }
    fun awaitVision(window:WindowSnapshot,message:String):Boolean {
        val retained=graph.coordinator.awaitVision(window,message)
        if(!retained)cancelOperation()
        publish(); return retained
    }
    fun pauseCapture(message:String) { cancelOperation();graph.coordinator.pauseCapture(message); publish() }
    fun invalidate(reason:String="会话已变化") { cancelOperation(LogReason.CONTEXT_CHANGED);graph.coordinator.invalidate();Logs.event(LogStep.UI_ACTION,LogState.WARN,LogReason.CONTEXT_CHANGED);publish() }
    fun restore(saved:SavedConversationReplies,windowId:Int):Boolean {
        cancelOperation(LogReason.CONTEXT_CHANGED)
        val restored=graph.coordinator.restore(saved,windowId)
        if(restored) { lastContext=Context(saved.snapshot.copy(windowId=windowId),"","",null,saved.target);publish() }
        return restored
    }
}
