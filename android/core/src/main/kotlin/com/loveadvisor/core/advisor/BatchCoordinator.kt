package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import com.loveadvisor.core.capture.WindowSnapshot
class BatchCoordinator(private val tracker:SessionTracker,private val gate:RequestGate) {
    @Synchronized fun pauseCapture(message:String) {
        // Pausing screen capture revokes pending work, not the user's last manual read.
        // Only a successful new read or explicit invalidation replaces that context.
        gate.cancel(); active=null
        state=state.copy(loading=false,error=message,isFailure=false)
    }
    var state=BatchState(); private set
    private var active:RequestTicket?=null
    private val history=ReplyHistory()
    private var historySession:SessionToken?=null
    private var target:ReplyTarget?=null
    private var timeScope:String?=null
    @Synchronized fun targetFor(snapshot:ChatSnapshot):ReplyTarget?=target.takeIf { snapshot.identity?.kind in setOf(ChatKind.DIRECT,ChatKind.GROUP) && tracker.lastSnapshot==snapshot && historySession==tracker.currentToken }
    @Synchronized fun observe(snapshot:ChatSnapshot,message:String?=null) {
        val before=tracker.currentToken
        val token=tracker.update(snapshot) ?: run { invalidate(); return }
        if(before==token) return
        gate.cancel(); active=null
        val keep=state.current?.takeIf { tracker.sameConversation(it.ticket.session) }
        if(keep==null) { history.clear(); historySession=null }
        state=BatchState(keep,loading=false,error=message)
    }
    @Synchronized fun status(message:String,failure:Boolean=false) { state=state.copy(error=message,isFailure=failure) }
    @Synchronized fun cancelPending(message:String) { gate.cancel(); active=null; state=state.copy(loading=false,error=message,isFailure=false) }
    @Synchronized fun awaitVision(window:WindowSnapshot,message:String):Boolean {
        val token=tracker.currentToken
        val same=token!=null && token.packageName==window.packageName && token.windowId==window.windowId
        if(same) status(message) else cancelPending(message)
        return same
    }
    @Synchronized fun begin(snapshot:ChatSnapshot,replyTarget:ReplyTarget?=null):RefreshRequest? {
        val token=tracker.update(snapshot) ?: run { invalidate(); return null }
        if(state.loading && active?.session==token && target==replyTarget) return null
        val scope=ConversationTimePolicy.scopeKey(snapshot,replyTarget)
        val changed=target!=replyTarget || timeScope!=scope
        if(historySession!=token || changed) { history.clear(); historySession=token }
        target=replyTarget
        timeScope=scope
        val previous=state.current?.takeIf { it.ticket.session==token && !changed }
        val ticket=gate.begin(token); active=ticket
        state=state.copy(current=state.current?.takeIf { tracker.sameConversation(it.ticket.session) },loading=true,error=null,isFailure=false)
        return RefreshRequest(ticket,previous==null,history.previousTexts,previous?.batch?.analysis)
    }
    fun complete(ticket:RequestTicket,batch:AdvisorBatch):Boolean=completeResult(ticket,batch)==BatchCompletion.ACCEPTED
    @Synchronized fun completeResult(ticket:RequestTicket,batch:AdvisorBatch):BatchCompletion {
        if(active!=ticket || !gate.accept(ticket)) return BatchCompletion.STALE
        val error=when {
            !AdvisorValidator().validate(batch).valid -> "候选回复格式不完整，请重新生成"
            history.previousTexts.isNotEmpty() && batch.replies.all { r -> history.previousTexts.any { AdvisorValidator.normalize(it)==AdvisorValidator.normalize(r.text) } } -> "未生成新的候选，请重试"
            else -> null
        }
        if(error!=null) { fail(ticket,error); return BatchCompletion.REJECTED }
        state=BatchState(VisibleBatch(ticket,batch)); active=null; history.add(batch); return BatchCompletion.ACCEPTED
    }
    @Synchronized fun fail(ticket:RequestTicket,message:String) {
        if(active!=ticket || !gate.accept(ticket)) return
        state=state.copy(loading=false,error=message,isFailure=true); active=null
    }
    @Synchronized fun invalidate() {
        gate.cancel(); tracker.invalidate("capture-invalidated"); active=null
        history.clear(); historySession=null; timeScope=null; state=BatchState()
    }
}
