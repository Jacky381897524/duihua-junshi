package com.loveadvisor.core.memory

import com.loveadvisor.core.model.*

data class InteractionPresentation(val contactId:String?=null,val estimate:Double?=null,val evidenceCount:Int=0,val relationship:String="未知",val lastEvidenceAt:Long?=null,val paused:Boolean=false,val group:Boolean=false)
object InteractionPresenter {
    fun build(state:RealityMemoryState,snapshot:ChatSnapshot,target:ReplyTarget?=null):InteractionPresentation {
        if(snapshot.source==CaptureSource.PASTE)return InteractionPresentation()
        val group=snapshot.identity?.kind==ChatKind.GROUP
        val raw=if(group) {
            val sender=target?.let { t -> snapshot.messages.find { it.id==t.messageId }?.sender } ?: return InteractionPresentation(group=true)
            PersonalIdentity.key(snapshot.packageName,sender,snapshot.identity!!.displayTitle)
        } else PersonalIdentity.root(snapshot) ?: return InteractionPresentation()
        val id=state.data.aliases[raw] ?: raw
        val contact=state.data.contacts.find { it.id==id }
        val signals=state.data.signals.filter { it.contactId==id }
        return InteractionPresentation(id,if(signals.isEmpty())null else InteractionTrend.score(signals),signals.size,contact?.let { PersonalRelations.context(it,state.data.memories).relationship } ?: "未知",signals.maxOfOrNull { it.at },state.paused || !state.learningEnabled,group)
    }
}
