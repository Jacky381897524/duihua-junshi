package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.google.gson.Gson

/** One last successful batch per confirmed conversation, independent of copy/fill actions. */
/** A non-reversible local fingerprint of the header, scoped to the window dimensions. */
data class CachedHeaderProof(val stamp:String,val width:Int,val height:Int,val conversationStamp:String?=null)
data class SavedConversationReplies(val snapshot:ChatSnapshot,val batch:AdvisorBatch,val target:ReplyTarget?=null,val savedAt:Long=System.currentTimeMillis(),val headerProof:CachedHeaderProof?=null,val headerHistory:List<CachedHeaderProof>?=emptyList())
interface ConversationReplyPersistence { fun load():List<SavedConversationReplies>;fun save(entries:List<SavedConversationReplies>) }
class ConversationReplyCache(private val persistence:ConversationReplyPersistence?=null,private val limit:()->Int={10}) {
    private val entries=linkedMapOf<String,SavedConversationReplies>()
    init { persistence?.load()?.filter(::valid)?.sortedBy { it.savedAt }?.forEach(::put); trim() }
    private fun key(s:ChatSnapshot)="${s.packageName}\u0000${s.identity?.kind}\u0000${s.identity?.id}"
    private fun sameConversation(a:ChatSnapshot,b:ChatSnapshot)=key(a)==key(b) ||
        a.packageName==b.packageName && a.identity?.kind==b.identity?.kind && a.identity?.displayTitle==b.identity?.displayTitle &&
            com.loveadvisor.core.capture.ConversationContextPolicy.strongOverlap(a.messages,b.messages)
    private fun sameCapturedView(a:SavedConversationReplies,b:SavedConversationReplies):Boolean =
        a.snapshot.packageName==b.snapshot.packageName && a.snapshot.identity?.kind==b.snapshot.identity?.kind &&
        a.snapshot.identity?.displayTitle==b.snapshot.identity?.displayTitle && proofs(a).any { first ->
            first.conversationStamp!=null && proofs(b).any { second -> first.stamp==second.stamp && first.width==second.width && first.height==second.height && first.conversationStamp==second.conversationStamp }
        }
    private fun trim():Boolean { var changed=false;while(entries.size>limit().coerceIn(1,20)) { entries.remove(entries.keys.first());changed=true };return changed }
    @Synchronized fun enforceLimit() { if(trim())persistence?.save(entries.values.toList()) }
    // Gson can construct partial/null fields from damaged or older documents. Skip a bad
    // entry rather than preventing application startup or discarding other contacts.
    private fun valid(e:SavedConversationReplies)=runCatching {
        val snapshot=e.snapshot
        snapshot.source!=CaptureSource.PASTE && snapshot.packageName.isNotBlank() && snapshot.identity?.confirmed==true &&
            snapshot.identity.kind in setOf(ChatKind.DIRECT,ChatKind.GROUP) && snapshot.identity.id.isNotBlank() && !snapshot.identity.displayTitle.isNullOrBlank() &&
            snapshot.messages.isNotEmpty() && snapshot.messages.map { it.id }.distinct().size==snapshot.messages.size &&
            snapshot.identity.kind==e.batch.kind && AdvisorValidator().validate(e.batch).valid
    }.getOrDefault(false)
    @Synchronized fun remember(value:SavedConversationReplies):Boolean {
        if(!valid(value))return false
        put(value)
        trim()
        persistence?.save(entries.values.toList())
        return true
    }
    private fun put(incoming:SavedConversationReplies) {
        val value=incoming.copy(batch=incoming.batch.copy(personalContext=incoming.batch.personalContext?.copy(interaction=emptyList())))
        // Repeated manual reads can receive new ephemeral IDs. A shared, substantial
        // bidirectional passage proves continuity; a matching nickname alone cannot.
        val matches=entries.filterValues { sameConversation(it.snapshot,value.snapshot) || sameCapturedView(it,value) }
        val old=matches.values.maxByOrNull { it.savedAt }
        matches.keys.forEach(entries::remove)
        val id=key(value.snapshot)
        val previous=old?.headerProof.takeIf { old?.snapshot?.identity?.displayTitle==value.snapshot.identity?.displayTitle }
        val current=value.headerProof
        val proof=when {
            current==null -> previous
            current.conversationStamp==null && previous!=null && current.stamp==previous.stamp && current.width==previous.width && current.height==previous.height -> current.copy(conversationStamp=previous.conversationStamp)
            else -> current
        }
        val history=(matches.values.flatMap(::proofs)+proofs(value)+listOfNotNull(proof)).distinct().takeLast(4)
        entries[id]=value.copy(headerProof=proof,headerHistory=history)
    }
    @Synchronized fun find(packageName:String,title:String):SavedConversationReplies? = entries.values.filter { it.snapshot.packageName==packageName && it.snapshot.identity?.displayTitle==title.trim() }.singleOrNull()
    @Synchronized fun findByIdentity(snapshot:ChatSnapshot):SavedConversationReplies? = entries[key(snapshot)]?.takeIf { it.snapshot.identity?.displayTitle==snapshot.identity?.displayTitle }
    private fun proofs(entry:SavedConversationReplies)=(entry.headerHistory.orEmpty()+listOfNotNull(entry.headerProof)).distinct()
    @Synchronized fun matchesIdentityHeader(snapshot:ChatSnapshot,proof:CachedHeaderProof):Boolean =
        entries[key(snapshot)]?.let { entry -> entry.snapshot.identity?.displayTitle==snapshot.identity?.displayTitle && proofs(entry).any { it.stamp==proof.stamp && it.width==proof.width && it.height==proof.height } }==true
    @Synchronized fun findByHeaderProof(packageName:String,proof:CachedHeaderProof):SavedConversationReplies? {
        val scoped=entries.values.filter { entry -> entry.snapshot.packageName==packageName && proofs(entry).any { it.width==proof.width && it.height==proof.height } }
        // Body similarity alone can recur in unrelated chats. Always require
        // the current header fingerprint as well before restoring a contact.
        val headerMatches=scoped.filter { entry -> proofs(entry).any { it.stamp==proof.stamp && it.width==proof.width && it.height==proof.height } }
        if(proof.conversationStamp!=null) {
            val bodyMatches=headerMatches.filter { entry -> proofs(entry).any { it.stamp==proof.stamp && it.width==proof.width && it.height==proof.height && it.conversationStamp==proof.conversationStamp } }
            if(bodyMatches.size==1)return bodyMatches.single()
            if(bodyMatches.size>1)return null
        }
        val unique=headerMatches.singleOrNull()
        if(unique!=null && find(packageName,unique.snapshot.identity!!.displayTitle!!)!=null)return unique
        // For repeated names, a matching header is insufficient.
        return null
    }
    @Synchronized fun hasAmbiguousTitle(packageName:String,title:String)=entries.values.count { it.snapshot.packageName==packageName && it.snapshot.identity?.displayTitle==title.trim() }>1
    @Synchronized fun bindHeaderProof(snapshot:ChatSnapshot,proof:CachedHeaderProof) {
        if(!Regex("[0-9a-f]{64}").matches(proof.stamp) || proof.conversationStamp?.let { !Regex("[0-9a-f]{64}").matches(it) }==true || proof.width<=0 || proof.height<=0)return
        val id=key(snapshot);val existing=entries[id] ?: return
        if(existing.snapshot.identity?.displayTitle!=snapshot.identity?.displayTitle || existing.headerProof==proof)return
        entries[id]=existing.copy(headerProof=proof,headerHistory=(proofs(existing)+proof).distinct().takeLast(4));persistence?.save(entries.values.toList())
    }
    @Synchronized fun touch(value:SavedConversationReplies) {
        val id=key(value.snapshot);val existing=entries.remove(id) ?: return
        entries[id]=existing.copy(savedAt=System.currentTimeMillis());persistence?.save(entries.values.toList())
    }
    @Synchronized fun hasEntries()=entries.isNotEmpty()
    @Synchronized fun all()=entries.values.toList()
    companion object {
        fun decode(raw:String):List<SavedConversationReplies> = Gson().fromJson(raw,Array<SavedConversationReplies?>::class.java)?.filterNotNull().orEmpty()
    }
}
