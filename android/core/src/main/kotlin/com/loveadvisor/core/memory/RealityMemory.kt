package com.loveadvisor.core.memory

import com.loveadvisor.core.model.*
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.sqrt

enum class PersonalKind { LIKE, DISLIKE, HABIT, TRAIT, BOUNDARY, RELATIONSHIP, EVENT, FACT, VOICE }
enum class EvidenceStatus { OBSERVED, TENTATIVE, CONFIRMED }
data class PersonalContact(val id:String,val platform:String,val nickname:String,val group:String?=null,val relationship:String="未知",val relationshipConfirmed:Boolean=false,val groupRoom:Boolean=false)
data class PersonalEvidence(val id:String,val ownerId:String,val peerId:String,val text:String,val time:MessageTime?,val readAt:Long,val speaker:Speaker)
data class PersonalMemory(val id:String,val ownerId:String,val kind:PersonalKind,val text:String,val sourceId:String?,val status:EvidenceStatus=EvidenceStatus.OBSERVED,val updatedAt:Long=0,val locked:Boolean=false)
data class InteractionSignal(val sourceId:String,val contactId:String,val category:String,val at:Long,val delta:Double)
data class VoiceCorrection(val id:String,val peerId:String,val original:String,val actual:String,val at:Long)
data class PersonalData(val contacts:List<PersonalContact> = emptyList(),val evidence:List<PersonalEvidence> = emptyList(),val memories:List<PersonalMemory> = emptyList(),val signals:List<InteractionSignal> = emptyList(),val corrections:List<VoiceCorrection> = emptyList(),val classified:Set<String> = emptySet(),val aliases:Map<String,String> = emptyMap(),val blocked:Set<String> = emptySet())
data class RealityMemoryState(val schema:Int=1,val learningEnabled:Boolean=true,val paused:Boolean=false,val revision:Long=0,val data:PersonalData=PersonalData(),val undo:PersonalData?=null)
interface RealityPersistence { fun load():RealityMemoryState?; fun save(state:RealityMemoryState) }
data class PersonalProposal(val sourceId:String,val kind:PersonalKind,val quote:String)
data class LearningSlice(val revision:Long,val evidence:List<PersonalEvidence>)
data class LearningReceipt(val added:Int,val contactId:String?,val revision:Long)

/** Names are deliberate identity keys, not a fuzzy guess at a real person. Group members have a separate namespace. */
object PersonalIdentity {
    const val SELF="self"
    fun normalized(text:String)=Normalizer.normalize(text,Normalizer.Form.NFKC).trim().replace(Regex("\\s+")," ").lowercase(Locale.ROOT)
    fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun key(platform:String,nickname:String,group:String?=null)="person-"+digest("$platform\u0000${group?.let(::normalized).orEmpty()}\u0000${normalized(nickname)}")
    fun root(snapshot:ChatSnapshot):String?=snapshot.identity?.displayTitle?.takeIf { it.isNotBlank() }?.let { key(snapshot.packageName,it,if(snapshot.identity.kind==ChatKind.GROUP)"group-root" else null) }
}

/** Encrypted persistence is supplied by Android. No write occurs on a getter or on generation/refresh. */
class RealityMemoryRepository(private val persistence:RealityPersistence) {
    private var value:RealityMemoryState?=null
    @Synchronized fun cachedRevision():Long?=value?.revision
    @Synchronized fun state():RealityMemoryState=value ?: (persistence.load() ?: RealityMemoryState()).also { value=it }
    private fun commit(next:RealityMemoryState) { persistence.save(next);value=next }
    private fun change(data:PersonalData,undoable:Boolean=true) { val old=state();commit(old.copy(revision=old.revision+1,data=data,undo=if(undoable)old.data else old.undo)) }
    @Synchronized fun configure(enabled:Boolean,paused:Boolean) { val s=state();commit(s.copy(learningEnabled=enabled,paused=paused,revision=s.revision+1)) }
    private fun canonical(key:String)=state().data.aliases[key] ?: key
    @Synchronized fun contactId(snapshot:ChatSnapshot):String?=PersonalIdentity.root(snapshot)?.let(::canonical)
    @Synchronized fun ingest(snapshot:ChatSnapshot,now:Long=System.currentTimeMillis()):LearningReceipt {
        val old=state();val rootKey=PersonalIdentity.root(snapshot) ?: return LearningReceipt(0,null,old.revision)
        if(!old.learningEnabled || old.paused)return LearningReceipt(0,canonical(rootKey),old.revision)
        val title=snapshot.identity!!.displayTitle!!
        var data=old.data
        val contacts=data.contacts.toMutableList()
        fun ensure(raw:String,name:String,group:String?,room:Boolean=false):String {
            val id=canonical(raw)
            if(contacts.none { it.id==id })contacts.add(PersonalContact(id,snapshot.packageName,name,group,groupRoom=room))
            return id
        }
        val group=snapshot.identity.kind==ChatKind.GROUP
        val root=ensure(rootKey,title,if(group)title else null,group)
        val known=(data.evidence.map { it.id }+data.blocked).toHashSet()
        fun semantic(owner:String,peer:String,text:String,time:MessageTime?)="$owner|$peer|${time?.epochMillis ?: "undated"}|${PersonalIdentity.normalized(text)}"
        val semantics=data.evidence.map { semantic(it.ownerId,it.peerId,it.text,it.time) }.toHashSet()
        val added=ArrayList<PersonalEvidence>()
        for(m in snapshot.messages) {
            if(m.kind!=MessageKind.TEXT || m.speaker==Speaker.UNKNOWN || m.text.isBlank() || m.text.length>3000)continue
            val peer=if(group && m.speaker==Speaker.OTHER) {
                val sender=m.sender?.takeIf { it.isNotBlank() } ?: continue
                ensure(PersonalIdentity.key(snapshot.packageName,sender,title),sender,title)
            } else root
            val owner=if(m.speaker==Speaker.ME)PersonalIdentity.SELF else peer
            // Message IDs and read time change on overlapping reads; they are not evidence identity.
            val rawPeer=if(group && m.speaker==Speaker.OTHER)PersonalIdentity.key(snapshot.packageName,m.sender!!,title) else rootKey
            val id=PersonalIdentity.digest("$rootKey|$rawPeer|${m.speaker}|${m.time?.epochMillis ?: "undated"}|${PersonalIdentity.normalized(m.text)}")
            if(!known.add(id) || !semantics.add(semantic(owner,peer,m.text,m.time)))continue
            added.add(PersonalEvidence(id,owner,peer,m.text,m.time,now,m.speaker))
        }
        if(added.isEmpty() && contacts==data.contacts)return LearningReceipt(0,root,old.revision)
        val automatic=added.mapNotNull { e -> LocalPersonalLearning.classify(e)?.let { kind ->
            val owner=if(kind==PersonalKind.RELATIONSHIP && !group)e.peerId else e.ownerId
            PersonalMemory(PersonalIdentity.digest("$owner|$kind|${PersonalIdentity.normalized(e.text)}"),owner,kind,e.text,e.id,if(kind in setOf(PersonalKind.RELATIONSHIP,PersonalKind.TRAIT))EvidenceStatus.TENTATIVE else EvidenceStatus.OBSERVED,now)
        } }
        val memories=(data.memories+automatic).distinctBy { it.id }
        var signals=data.signals
        for(e in added) {
            // Unknown time cannot establish a new daily interaction; old imported messages cannot inflate today's trend.
            val at=e.time?.epochMillis ?: continue
            if(e.speaker!=Speaker.OTHER || at>now+60_000 || now-at>24*3600_000L)continue
            val category=InteractionTrend.category(e.text) ?: continue
            val previous=signals.filter { it.contactId==e.ownerId }
            val delta=InteractionTrend.delta(previous,category,at)
            signals=signals+InteractionSignal(e.id,e.ownerId,category,at,delta)
        }
        data=data.copy(contacts=contacts,evidence=data.evidence+added,memories=memories,signals=signals)
        change(data)
        return LearningReceipt(added.size,root,state().revision)
    }
    @Synchronized fun pending(max:Int=24):LearningSlice {
        val s=state();return LearningSlice(s.revision,if(!s.learningEnabled || s.paused)emptyList() else s.data.evidence.filter { it.id !in s.data.classified && it.id !in s.data.blocked }.takeLast(max))
    }
    /** A completed classifier may not overwrite edits, pause, aliases or deletions made while it was running. */
    @Synchronized fun apply(slice:LearningSlice,proposals:List<PersonalProposal>,now:Long=System.currentTimeMillis()):Boolean {
        val s=state();if(!s.learningEnabled || s.paused || slice.revision!=s.revision)return false
        val available=slice.evidence.associateBy { it.id }
        val items=proposals.take(32).mapNotNull { p ->
            val e=available[p.sourceId] ?: return@mapNotNull null
            if(e.id in s.data.blocked)return@mapNotNull null
            val quote=p.quote.trim()
            if(quote.length !in 2..300 || !e.text.contains(quote) || !LocalPersonalLearning.permitted(p.kind,quote) || !LocalPersonalLearning.permitted(p.kind,e.text))return@mapNotNull null
            // Store the source verbatim, never a model-authored fact about a person.
            val group=s.data.contacts.find { it.id==e.peerId }?.group!=null
            if(p.kind==PersonalKind.RELATIONSHIP && group)return@mapNotNull null
            val owner=if(p.kind==PersonalKind.RELATIONSHIP)e.peerId else e.ownerId
            val id=PersonalIdentity.digest("$owner|${p.kind}|${PersonalIdentity.normalized(quote)}")
            if(id in s.data.blocked || s.data.memories.any { it.id==id && it.locked })return@mapNotNull null
            PersonalMemory(id,owner,p.kind,quote,e.id,if(p.kind in setOf(PersonalKind.TRAIT,PersonalKind.RELATIONSHIP))EvidenceStatus.TENTATIVE else EvidenceStatus.OBSERVED,now)
        }
        val existing=s.data.memories.map { it.id }.toSet()
        change(s.data.copy(memories=s.data.memories+items.filter { it.id !in existing }.distinctBy { it.id },classified=s.data.classified+available.keys))
        return true
    }
    @Synchronized fun edit(item:PersonalMemory,text:String) {
        require(text.isNotBlank() && text.length<=300) { "记忆需为1至300字" }
        val d=state().data;require(item.ownerId==PersonalIdentity.SELF || d.contacts.any { it.id==item.ownerId })
        val saved=item.copy(text=text.trim(),status=EvidenceStatus.CONFIRMED,locked=true,updatedAt=System.currentTimeMillis())
        change(d.copy(memories=d.memories.filterNot { it.id==item.id }+saved,blocked=d.blocked-item.id))
    }
    @Synchronized fun relationship(id:String,label:String) {
        require(label.isNotBlank() && label.length<=80)
        val d=state().data;require(d.contacts.any { it.id==id });change(d.copy(contacts=d.contacts.map { if(it.id==id)it.copy(relationship=label.trim(),relationshipConfirmed=true) else it }))
    }
    @Synchronized fun correction(peerId:String,original:String,actual:String) {
        require(actual.isNotBlank() && actual.length<=500 && original.length<=500)
        val d=state().data;require(d.contacts.any { it.id==peerId })
        val id=PersonalIdentity.digest("$peerId|$original|$actual")
        if(d.corrections.any { it.id==id })return
        change(d.copy(corrections=d.corrections+VoiceCorrection(id,peerId,original,actual,System.currentTimeMillis())))
    }
    @Synchronized fun dismissSignal(sourceId:String) { val d=state().data;change(d.copy(signals=d.signals.filterNot { it.sourceId==sourceId })) }
    @Synchronized fun deleteMemory(id:String) { val d=state().data;val item=d.memories.find { it.id==id } ?: return;change(d.copy(memories=d.memories.filterNot { it.id==id },blocked=d.blocked+id+listOfNotNull(item.sourceId))) }
    @Synchronized fun forget(id:String) {
        val d=state().data;val contact=d.contacts.find { it.id==id }
        val ids=if(contact?.groupRoom==true)d.contacts.filter { it.platform==contact.platform && it.group==contact.group }.map { it.id }.toSet() else setOf(id)
        val removed=d.evidence.filter { it.ownerId in ids || it.peerId in ids }
        val sourceIds=removed.map { it.id }.toSet()
        change(d.copy(contacts=d.contacts.map { if(it.id in ids)it.copy(relationship="未知",relationshipConfirmed=false) else it },evidence=d.evidence.filterNot { it.id in sourceIds },memories=d.memories.filterNot { it.ownerId in ids || it.sourceId in sourceIds },signals=d.signals.filterNot { it.contactId in ids || it.sourceId in sourceIds },corrections=d.corrections.filterNot { id==PersonalIdentity.SELF || it.peerId in ids },blocked=d.blocked+sourceIds))
    }
    @Synchronized fun clear() { val s=state();val d=s.data;commit(s.copy(revision=s.revision+1,data=PersonalData(blocked=d.blocked+d.evidence.map { it.id }+d.memories.map { it.id }),undo=null)) }
    @Synchronized fun undo():Boolean { val s=state();val previous=s.undo ?: return false;commit(s.copy(revision=s.revision+1,data=previous,undo=null));return true }
    /** Explicit user association; merge data and aliases together, without a fuzzy nickname match. */
    @Synchronized fun bind(observedId:String,targetId:String) {
        val d=state().data;val from=d.contacts.single { it.id==observedId };val target=d.contacts.single { it.id==targetId }
        require(from.platform==target.platform && from.group==target.group && from.groupRoom==target.groupRoom && !from.groupRoom && from.id!=target.id) { "请选择同平台、同群范围的另一个对象；群名不自动合并" }
        change(d.copy(contacts=d.contacts.filterNot { it.id==from.id },evidence=d.evidence.map { it.copy(ownerId=if(it.ownerId==from.id)target.id else it.ownerId,peerId=if(it.peerId==from.id)target.id else it.peerId) },memories=d.memories.map { if(it.ownerId==from.id)it.copy(ownerId=target.id) else it },signals=d.signals.map { if(it.contactId==from.id)it.copy(contactId=target.id) else it },corrections=d.corrections.map { if(it.peerId==from.id)it.copy(peerId=target.id) else it },aliases=d.aliases.mapValues { if(it.value==from.id)target.id else it.value }+(from.id to target.id)))
    }
}

object LocalPersonalLearning {
    private const val firstPerson="我(?:本人|自己)?(?:真的|比较|一直|平时|非常|特别|也|更|还|其实|很|最|挺|超|蛮|就|向来|有点){0,3}"
    fun permitted(kind:PersonalKind,text:String):Boolean {
        if(text.contains(Regex("[?？]|他说|她说|你说|假如|如果|例如|比如|指令|忽略|system|prompt",RegexOption.IGNORE_CASE)))return false
        return when(kind) {
            PersonalKind.LIKE -> text.contains(Regex(firstPerson+"(喜欢|爱吃|爱喝|最爱)")) && !text.contains(Regex("不喜欢|不爱|讨厌|以前|曾经"))
            PersonalKind.DISLIKE -> text.contains(Regex(firstPerson+"(不喜欢|讨厌|不爱|不吃|不喝)"))
            PersonalKind.HABIT -> text.contains(Regex(firstPerson+"(习惯|通常|每天|每晚|一般|总是)"))
            PersonalKind.BOUNDARY -> text.contains(Regex("不要再|别再|我不想|我不愿意|不接受"))
            PersonalKind.RELATIONSHIP -> text.contains(Regex("我们.{0,4}(朋友|同事|同学|夫妻|恋人|家人)|我是你.{0,3}(老婆|老公|同事|朋友)|只是朋友"))
            PersonalKind.TRAIT -> text.contains(Regex(firstPerson+"(性格|慢热|内向|外向|急性子)"))
            PersonalKind.EVENT -> text.length>=4
            PersonalKind.FACT -> text.startsWith("我")
            PersonalKind.VOICE -> false // voice samples come from actual ME messages/corrections, never model classifications
        }
    }
    fun classify(e:PersonalEvidence)=if(e.text.length>300)null else listOf(PersonalKind.DISLIKE,PersonalKind.LIKE,PersonalKind.HABIT,PersonalKind.BOUNDARY,PersonalKind.RELATIONSHIP,PersonalKind.TRAIT).firstOrNull { permitted(it,e.text) }
}

/** An interaction estimate, not a measurement of another person's feelings. No points for read/refresh/send alone. */
object InteractionTrend {
    fun category(text:String):String? {
        if(text.contains(Regex("[?？]|他说|她说|你说|如果|假如")))return null
        return when {
            text.contains(Regex("别再联系我|不要再找我|我不想见你|我(?:不喜欢|讨厌)你(?:了)?(?:$|[，。！!\\s])"))->"boundary"
            text.contains(Regex("谢谢你|感谢你|辛苦你了"))->"appreciation"
            text.contains(Regex("我(?:想|喜欢|爱)你(?:了|啦|呀|啊|呢|哦|～|~)?(?:$|[，。！!\\s])|很想见你")) && !text.contains("不")->"closeness"
            else->null
        }
    }
    fun score(signals:List<InteractionSignal>)=(50.0+signals.sumOf { it.delta }).coerceIn(0.0,100.0)
    private fun day(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
    fun delta(previous:List<InteractionSignal>,category:String,at:Long):Double {
        val sameDay=previous.filter { day(it.at)==day(at) }
        val repeats=sameDay.count { it.category==category }
        val base=if(category=="boundary")-2.0 else if(category=="closeness")1.5 else .6
        val change=base/sqrt(1.0+repeats)
        return if(change<=0)change.coerceAtLeast(-3.0) else minOf(change*((100-score(previous))/50).coerceIn(.15,1.0),(3.0-sameDay.sumOf { it.delta.coerceAtLeast(0.0) }).coerceAtLeast(0.0))
    }
}
