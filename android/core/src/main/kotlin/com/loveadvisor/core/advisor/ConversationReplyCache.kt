package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.google.gson.Gson

/** One last successful batch per confirmed conversation, independent of copy/fill actions. */
data class SavedConversationReplies(val snapshot:ChatSnapshot,val batch:AdvisorBatch,val target:ReplyTarget?=null,val savedAt:Long=System.currentTimeMillis())
interface ConversationReplyPersistence { fun load():List<SavedConversationReplies>;fun save(entries:List<SavedConversationReplies>) }
class ConversationReplyCache(private val persistence:ConversationReplyPersistence?=null) {
    private val entries=linkedMapOf<String,SavedConversationReplies>()
    init { persistence?.load()?.filter(::valid)?.forEach { entries[key(it.snapshot)]=it } }
    private fun key(s:ChatSnapshot)="${s.packageName}\u0000${s.identity?.kind}\u0000${s.identity?.id}"
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
        entries[key(value.snapshot)]=value
        persistence?.save(entries.values.toList())
        return true
    }
    @Synchronized fun find(packageName:String,title:String):SavedConversationReplies? = entries.values.filter { it.snapshot.packageName==packageName && it.snapshot.identity?.displayTitle==title.trim() }.singleOrNull()
    @Synchronized fun hasEntries()=entries.isNotEmpty()
    @Synchronized fun all()=entries.values.toList()
    companion object {
        fun decode(raw:String):List<SavedConversationReplies> = Gson().fromJson(raw,Array<SavedConversationReplies?>::class.java)?.filterNotNull().orEmpty()
    }
}
