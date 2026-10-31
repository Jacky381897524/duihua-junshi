package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.google.gson.Gson

/** One last successful batch per confirmed conversation, independent of copy/fill actions. */
/** A non-reversible local fingerprint of the header, scoped to the window dimensions. */
data class CachedHeaderProof(val stamp:String,val width:Int,val height:Int)
data class SavedConversationReplies(val snapshot:ChatSnapshot,val batch:AdvisorBatch,val target:ReplyTarget?=null,val savedAt:Long=System.currentTimeMillis(),val headerProof:CachedHeaderProof?=null)
interface ConversationReplyPersistence { fun load():List<SavedConversationReplies>;fun save(entries:List<SavedConversationReplies>) }
class ConversationReplyCache(private val persistence:ConversationReplyPersistence?=null,private val limit:()->Int={10}) {
    private val entries=linkedMapOf<String,SavedConversationReplies>()
    init { persistence?.load()?.filter(::valid)?.sortedBy { it.savedAt }?.forEach { entries[key(it.snapshot)]=it }; trim() }
    private fun key(s:ChatSnapshot)="${s.packageName}\u0000${s.identity?.kind}\u0000${s.identity?.id}"
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
        val id=key(value.snapshot);val old=entries.remove(id)
        entries[id]=if(value.headerProof==null && old?.snapshot?.identity?.displayTitle==value.snapshot.identity?.displayTitle)value.copy(headerProof=old?.headerProof) else value
        trim()
        persistence?.save(entries.values.toList())
        return true
    }
    @Synchronized fun find(packageName:String,title:String):SavedConversationReplies? = entries.values.filter { it.snapshot.packageName==packageName && it.snapshot.identity?.displayTitle==title.trim() }.singleOrNull()
    @Synchronized fun findByHeaderProof(packageName:String,proof:CachedHeaderProof):SavedConversationReplies? {
        val match=entries.values.filter { it.snapshot.packageName==packageName && it.headerProof==proof }.singleOrNull() ?: return null
        // An identical title cannot distinguish two different contacts, even if only one has a fingerprint.
        return match.takeIf { find(packageName,it.snapshot.identity!!.displayTitle!!)!=null }
    }
    @Synchronized fun bindHeaderProof(snapshot:ChatSnapshot,proof:CachedHeaderProof) {
        if(!Regex("[0-9a-f]{64}").matches(proof.stamp) || proof.width<=0 || proof.height<=0)return
        val id=key(snapshot);val existing=entries[id] ?: return
        if(existing.snapshot.identity?.displayTitle!=snapshot.identity?.displayTitle || existing.headerProof==proof)return
        entries[id]=existing.copy(headerProof=proof);persistence?.save(entries.values.toList())
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
