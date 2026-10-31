package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.SessionTracker

/** The local header lookup used when reopening a panel. No model or chat read is involved. */
class PanelReplyRestoration(
    private val coordinator:BatchCoordinator,
    private val tracker:SessionTracker,
    private val cache:ConversationReplyCache
) {
    class Check internal constructor(
        val packageName:String,val windowId:Int,
        internal val token:SessionToken?,internal val snapshot:ChatSnapshot?,
        internal val state:BatchState
    )
    enum class Result { RETAINED,RESTORED,UNAVAILABLE,STALE }
    private var pending:Check?=null
    private val checkingRetained="正在核对对象 · 已保留上次六条候选"
    private val checkingCache="正在核对对象，恢复本地候选…"
    private val unverified="标题暂未核对 · 已保留已读对象候选，填入时再次核对"

    @Synchronized fun begin(packageName:String,windowId:Int):Check {
        val snapshot=tracker.lastSnapshot
        val visible=coordinator.state.current
        // Closing a View is not navigation. Keep the live batch, target and refresh
        // history while checking its title; only a different window revokes it here.
        val retain=snapshot?.packageName==packageName && snapshot.windowId==windowId &&
            snapshot.identity?.confirmed==true && visible!=null && tracker.sameConversation(visible.ticket.session)
        if(!retain)coordinator.invalidate()
        val check=Check(packageName,windowId,tracker.currentToken,tracker.lastSnapshot,coordinator.state)
        pending=check
        coordinator.status(if(retain)checkingRetained else checkingCache)
        return check
    }

    @Synchronized fun complete(check:Check,title:String?,nativeTitle:Boolean):Result {
        if(pending!==check || tracker.currentToken!=check.token || tracker.lastSnapshot!=check.snapshot ||
            coordinator.state.current!==check.state.current || coordinator.state.loading)return Result.STALE
        pending=null
        val name=title?.trim()?.takeIf { it.isNotEmpty() }
        val retained=check.state.current!=null && check.snapshot!=null
        if(retained && name!=null && name==check.snapshot?.identity?.displayTitle) {
            val previous=check.state.error?.takeUnless { it==checkingRetained || it==checkingCache || it==unverified }
            coordinator.status(previous ?: "已保留本对象上次候选 · 未调用模型")
            return Result.RETAINED
        }
        val saved=name?.let { cache.find(check.packageName,it) }
        if(saved!=null && coordinator.restore(saved,check.windowId)) { cache.touch(saved);return Result.RESTORED }
        // An absent/incorrect OCR nickname is not proof of changing contacts.
        // Keep the clearly labelled last read. Fill still verifies the live title,
        // input region and draft independently before any text is written.
        if(retained && (name==null || !nativeTitle)) {
            coordinator.status(unverified)
            return Result.RETAINED
        }
        coordinator.invalidate()
        coordinator.status("此对象暂无可确认的本地候选，点击读取信息；其他对象候选已保留")
        return Result.UNAVAILABLE
    }
}
