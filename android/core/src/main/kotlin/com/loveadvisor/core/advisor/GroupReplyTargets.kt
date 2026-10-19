package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
object GroupReplyTargets {
    fun valid(input:AdvisorInput,plans:List<ReplyPlan>):Boolean {
        if(input.snapshot.identity?.kind!=ChatKind.GROUP)return input.replyTarget==null
        val targets=input.snapshot.messages.filter { it.speaker==Speaker.OTHER }.map { it.id }.toSet()
        val selected=input.replyTarget?.messageId
        return (selected==null || selected in targets) && plans.all { it.targetMessageId in targets && (selected==null || it.targetMessageId==selected) && !it.topic.isNullOrBlank() && it.topic.length<=40 }
    }
}
