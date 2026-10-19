package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
object ReplyTargets {
    fun valid(input:AdvisorInput,plans:List<ReplyPlan>):Boolean {
        val targets=input.snapshot.messages.filter { it.speaker==Speaker.OTHER }.map { it.id }.toSet()
        val selected=input.replyTarget?.messageId
        if(input.snapshot.identity?.kind!=ChatKind.GROUP)return if(selected==null)plans.all { it.targetMessageId==null }
            else input.snapshot.identity?.kind==ChatKind.DIRECT && selected in targets && plans.all { it.targetMessageId==selected }
        return (selected==null || selected in targets) && plans.all { it.targetMessageId in targets && (selected==null || it.targetMessageId==selected) && !it.topic.isNullOrBlank() && it.topic.length<=40 }
    }
}
