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
        internal val state:BatchState,internal val navigationCheck:Boolean,internal val expectedCurrent:VisibleBatch?
    )
    enum class Result { RETAINED,RESTORED,UNAVAILABLE,STALE }
    private var pending:Check?=null
    private val checkingRetained="正在核对对象 · 已保留上次六条候选"
    private val checkingCache="正在核对对象，恢复本地候选…"
    private val unverified="标题暂未核对 · 已保留已读对象候选，填入时再次核对"

    @Synchronized fun begin(packageName:String,windowId:Int,navigationCheck:Boolean=false):Check {
        val snapshot=tracker.lastSnapshot
        val visible=coordinator.state.current
        // Closing a View is not navigation. Keep the live batch, target and refresh
        // history while checking its title; only a different window revokes it here.
        val retain=snapshot?.packageName==packageName && snapshot.windowId==windowId &&
            snapshot.identity?.confirmed==true && visible!=null && tracker.sameConversation(visible.ticket.session)
        if(retain && navigationCheck && snapshot!=null && visible!=null && cache.findByIdentity(snapshot)==null) {
            cache.remember(SavedConversationReplies(snapshot,visible.batch,coordinator.targetFor(snapshot)))
        }
        if(!retain)coordinator.invalidate()
        val original=coordinator.state
        if(navigationCheck)coordinator.holdForIdentityCheck(checkingCache)
        val check=Check(packageName,windowId,tracker.currentToken,tracker.lastSnapshot,original,navigationCheck,coordinator.state.current)
        pending=check
        if(!navigationCheck)coordinator.status(if(retain)checkingRetained else checkingCache)
        return check
    }

    @Synchronized fun complete(check:Check,title:String?,nativeTitle:Boolean,proof:CachedHeaderProof?=null):Result {
        if(pending!==check || tracker.currentToken!=check.token || tracker.lastSnapshot!=check.snapshot ||
            coordinator.state.current!==check.expectedCurrent || coordinator.state.loading)return Result.STALE
        pending=null
        val name=title?.trim()?.takeIf { it.isNotEmpty() }
        val retained=check.state.current!=null && check.snapshot!=null
        val byProof=proof?.let { cache.findByHeaderProof(check.packageName,it) }
        if(byProof!=null && byProof.snapshot.identity?.displayTitle==name) {
            if(retained && byProof.snapshot.identity?.id==check.snapshot?.identity?.id) {
                if(check.navigationCheck && !coordinator.resumeAfterIdentityCheck(check.state.current!!))return Result.STALE
                coordinator.status("已保留本对象上次候选 · 未调用模型")
                return Result.RETAINED
            }
            if(coordinator.restore(byProof,check.windowId)) { cache.touch(byProof);return Result.RESTORED }
        }
        val ambiguous=name!=null && cache.hasAmbiguousTitle(check.packageName,name)
        if(retained && name!=null && name==check.snapshot?.identity?.displayTitle && !ambiguous) {
            if(check.navigationCheck && !coordinator.resumeAfterIdentityCheck(check.state.current!!))return Result.STALE
            val previous=check.state.error?.takeUnless { it==checkingRetained || it==checkingCache || it==unverified }
            coordinator.status(previous ?: "已保留本对象上次候选 · 未调用模型")
            return Result.RETAINED
        }
        val saved=name?.let { cache.find(check.packageName,it) }
        if(saved!=null && coordinator.restore(saved,check.windowId)) { cache.touch(saved);return Result.RESTORED }
        // Reopening the same panel alone is not navigation. During a background
        // navigation check, however, an unknown or different OCR title must not
        // leave another contact's replies on screen. The cache remains untouched.
        if(retained && !ambiguous && !check.navigationCheck && name==null) {
            coordinator.status(unverified)
            return Result.RETAINED
        }
        // Even a previously unpersisted successful batch can be restored later.
        if(retained && check.snapshot!=null && check.state.current!=null) {
            cache.remember(SavedConversationReplies(check.snapshot,check.state.current.batch,coordinator.targetFor(check.snapshot)))
        }
        coordinator.invalidate()
        coordinator.status("此对象暂无可确认的本地候选，点击读取信息；其他对象候选已保留")
        return Result.UNAVAILABLE
    }
}
