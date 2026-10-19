package com.loveadvisor.core.capture
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.core.model.*

/** Persisted through the Android encrypted document store. Names never identify a contact alone. */
class RecentConversations {
    var entries:List<ChatSnapshot> = emptyList(); private set
    var lastCleanup:Long=0; private set
    fun remember(snapshot:ChatSnapshot) {
        require(snapshot.identity?.kind in setOf(ChatKind.DIRECT,ChatKind.GROUP))
        entries=entries.filterNot { it.packageName==snapshot.packageName && it.identity?.id==snapshot.identity!!.id }+snapshot.copy(messages=snapshot.messages.takeLast(ConversationContextPolicy.forSnapshot(snapshot)))
    }
    fun cleanup(now:Long) { if(now-lastCleanup>=12*3600000L || lastCleanup==0L) { entries=entries.map { it.copy(messages=it.messages.takeLast(ConversationContextPolicy.forSnapshot(it))) }; lastCleanup=now } }
    fun match(visible:ChatSnapshot):ChatSnapshot? {
        val messages=visible.messages
        val participants=messages.map { if(it.speaker==Speaker.ME) "me" else it.sender?.let { name -> "other:$name" } ?: "other" }.distinct()
        if(messages.size<3 || participants.size<2) return null
        fun equal(a:ChatMessage,b:ChatMessage)=ConversationContextPolicy.same(a,b)
        val matches=entries.filter { entry ->
            if(entry.packageName!=visible.packageName || entry.identity?.kind!=visible.identity?.kind || entry.identity?.displayTitle!=visible.identity?.displayTitle) false else {
                (3..minOf(entry.messages.size,messages.size)).any { count ->
                    val rows=messages.takeLast(count)
                    rows.sumOf { it.text.length }>=60 && rows.map { it.text }.distinct().size>=3 &&
                        (0..entry.messages.size-count).any { start -> rows.indices.all { equal(entry.messages[start+it],rows[it]) } }
                }
            }
        }
        return matches.singleOrNull()
    }
    fun encode()=Gson().toJson(this)
    companion object {
        fun decode(raw:String):RecentConversations=runCatching {
            val tree=JsonParser.parseString(raw).asJsonObject
            tree.getAsJsonArray("entries")?.forEach { entry ->
                val item=entry.asJsonObject
                item.getAsJsonArray("messages")?.forEach { message ->
                    val row=message.asJsonObject
                    val kind=row["kind"]?.takeIf { it.isJsonPrimitive }?.asString
                    if(kind==null || MessageKind.entries.none { it.name==kind })row.addProperty("kind",if(kind==null)"TEXT" else "UNKNOWN")
                }
            }
            Gson().fromJson(tree,RecentConversations::class.java).apply {
                entries=entries.filter { it.identity?.kind in setOf(ChatKind.DIRECT,ChatKind.GROUP) }.map {
                    it.copy(contextLimit=ConversationContextPolicy.forSnapshot(it),messages=it.messages.takeLast(ConversationContextPolicy.forSnapshot(it)))
                }
            }
        }.getOrDefault(RecentConversations())
    }
}
