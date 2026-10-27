package com.loveadvisor.core.memory

import com.loveadvisor.core.model.*
import com.loveadvisor.core.advisor.ConversationTimePolicy
import com.google.gson.Gson

data class VoiceHabits(val sampleCount:Int=0,val medianLength:Int=0,val punctuationRatio:Double=0.0,val commonEndings:List<String> = emptyList())
data class PersonalContext(val contacts:List<PersonalContact> = emptyList(),val memories:List<ContextMemory> = emptyList(),val ownExamples:List<String> = emptyList(),val corrections:List<VoiceCorrection> = emptyList(),val interaction:List<ContextTrend> = emptyList(),val ownHabits:VoiceHabits=VoiceHabits())
data class ContextMemory(val owner:String,val category:PersonalKind,val original:String,val status:EvidenceStatus,val time:MessageTime?,val firstReadAt:Long?,val interpretation:String?=null)
data class ContextTrend(val contactId:String,val estimate:Double,val evidenceCount:Int,val lastEvidenceAt:Long?)

/** Local retrieval: current raw messages stay in their own field and are never replaced by remembered prose. */
object PersonalContextBuilder {
    const val BUDGET=4200
    fun build(state:RealityMemoryState,snapshot:ChatSnapshot,target:ReplyTarget?=null,now:Long=System.currentTimeMillis()):PersonalContext {
        val root=PersonalIdentity.root(snapshot) ?: return PersonalContext()
        val rootId=state.data.aliases[root] ?: root
        val d=state.data
        val group=snapshot.identity?.kind==ChatKind.GROUP
        val selected=target?.let { t -> snapshot.messages.find { it.id==t.messageId } }
        val names=if(group)listOfNotNull(selected?.sender).ifEmpty { snapshot.messages.takeLast(12).filter { it.speaker==Speaker.OTHER }.mapNotNull { it.sender }.distinct().takeLast(3) } else emptyList()
        val ids=if(group)names.map { PersonalIdentity.key(snapshot.packageName,it,snapshot.identity!!.displayTitle).let { key -> d.aliases[key] ?: key } } else listOf(rootId)
        val contacts=d.contacts.filter { it.id in ids }.map { PersonalRelations.context(it,d.memories).let { c -> c.copy(nickname=c.nickname.take(120),group=c.group?.take(120),relationship=c.relationship.take(80)) } }
        val query=(selected?.text ?: ConversationTimePolicy.currentMessages(snapshot,now).takeLast(6).joinToString(" ") { it.text })
        val tokens=grams(query)
        val sources=d.evidence.associateBy { it.id }
        fun relevance(text:String)=grams(text).count { it in tokens }
        val memories=d.memories.filter { m ->
            (m.ownerId==PersonalIdentity.SELF || m.ownerId in ids) && m.kind!=PersonalKind.VOICE &&
                (m.kind!=PersonalKind.EVENT && !(m.kind==PersonalKind.FACT && m.text.contains(Regex("今天|昨天|明天|刚才|现在|等下|今晚"))) || sources[m.sourceId]?.let { e ->
                    val at=e.time?.epochMillis ?: e.readAt
                    now-at<=24*3600_000L && relevance(m.text)>0
                }==true || m.kind==PersonalKind.EVENT && m.sourceId==null && now-m.updatedAt<=24*3600_000L && relevance(m.text)>0)
        }.sortedWith(compareByDescending<PersonalMemory> { it.locked }.thenByDescending { relevance(it.text)+(if(it.kind in setOf(PersonalKind.BOUNDARY,PersonalKind.DISLIKE))2 else 0) }.thenByDescending { it.updatedAt }).take(14).map { m ->
            val evidence=sources[m.sourceId]
            ContextMemory(m.ownerId,m.kind,m.text.take(220),m.status,evidence?.time,evidence?.readAt ?: m.updatedAt,m.interpretation)
        }
        // Actual examples stay within this contact; the fallback carries only global language statistics.
        val own=d.evidence.filter { it.ownerId==PersonalIdentity.SELF && it.id !in d.blocked && it.text.length in 2..160 }
        val scopedOwn=own.filter { it.peerId in ids || it.peerId==rootId }
        // Old content contributes language statistics, not an old topic copied into today's examples.
        val samples=scopedOwn.filter { now-(it.time?.epochMillis ?: it.readAt)<=24*3600_000L }.takeLast(5).distinctBy { PersonalIdentity.normalized(it.text) }.map { it.text }
        val learnedTexts=(scopedOwn.ifEmpty { own }.takeLast(100).map { it.text }+d.corrections.filter { it.peerId in ids || it.peerId==rootId }.takeLast(30).map { it.actual })
        val lengths=learnedTexts.map { it.codePointCount(0,it.length) }.sorted()
        val endings=listOf("哈哈","嗯","啦","呀","呢","啊","哦","吧","嘛","～","~").map { ending -> ending to learnedTexts.count { it.endsWith(ending) } }.filter { it.second>=2 }.sortedByDescending { it.second }.take(4).map { it.first }
        val habits=VoiceHabits(lengths.size,lengths.getOrNull(lengths.size/2) ?: 0,if(learnedTexts.isEmpty())0.0 else learnedTexts.count { it.contains(Regex("[，。！？,.!?]")) }.toDouble()/learnedTexts.size,endings)
        val corrections=d.corrections.filter { (it.peerId in ids || it.peerId==rootId) && now-it.at<=24*3600_000L }.takeLast(2)
        var context=PersonalContext(contacts,memories,samples,corrections,contacts.map { c -> val signals=d.signals.filter { it.contactId==c.id };ContextTrend(c.id,InteractionTrend.score(signals),signals.size,signals.maxOfOrNull { it.at }) },habits)
        val gson=Gson()
        while(gson.toJson(context).length>BUDGET && (context.memories.isNotEmpty() || context.ownExamples.isNotEmpty() || context.corrections.isNotEmpty())) {
            context=when {
                context.memories.size>5->context.copy(memories=context.memories.dropLast(1))
                context.ownExamples.isNotEmpty()->context.copy(ownExamples=context.ownExamples.dropLast(1))
                context.corrections.isNotEmpty()->context.copy(corrections=context.corrections.drop(1))
                else->context.copy(memories=context.memories.dropLast(1))
            }
        }
        return context
    }
    private fun grams(text:String):Set<String> { val s=PersonalIdentity.normalized(text).filter { it.isLetterOrDigit() };return if(s.length<2)setOf(s) else s.windowed(2).toSet() }
}
