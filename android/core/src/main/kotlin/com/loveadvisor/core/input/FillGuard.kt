package com.loveadvisor.core.input
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
data class FillDecision(val allowed:Boolean,val reason:String)
class FillGuard(private val tracker:SessionTracker,private val allowOlderRevision:Boolean=false) {
    fun isCurrent(token:SessionToken)=if(allowOlderRevision) tracker.sameConversation(token) else tracker.isCurrent(token)
    fun verify(token:SessionToken,current:ChatSnapshot?,window:WindowSnapshot?,editable:NodeRecord?):FillDecision {
        if(!isCurrent(token) || current==null || window==null || editable==null) return FillDecision(false,"当前聊天已变化")
        if(current.identity?.confirmed!=true || window.confirmedIdentity?.id!=token.conversationId || current.identity.id!=token.conversationId || current.packageName!=token.packageName || current.windowId!=token.windowId || window.packageName!=token.packageName || window.windowId!=token.windowId || current.messages!=tracker.lastSnapshot?.messages) return FillDecision(false,"当前聊天身份无法确认")
        // Reader-supplied identity is cached context, not evidence of the live page.
        // Missing native titles must use the independently verified visual/IME path.
        val title=current.identity.displayTitle
        if(ChatApps.supported(window.packageName) && (title.isNullOrBlank() || NativeReplyStyleTitle.read(window)!=title)) return FillDecision(false,"未取得匹配的当前昵称，需要本地核验")
        val v=window.viewport; val r=editable.bounds
        if(!editable.editable || editable.text.orEmpty().contains("搜索") || r.top<v.top+v.height*.55 || r.bottom>v.bottom || r.height>v.height*.2 || r.width<v.width*.25 || r.width>v.width*.95 || editable !in window.nodes) return FillDecision(false,"无法确认底部聊天输入框，已改为复制")
        return FillDecision(true,"")
    }
}
