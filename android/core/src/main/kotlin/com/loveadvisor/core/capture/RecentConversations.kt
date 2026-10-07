package com.loveadvisor.core.capture
import com.google.gson.Gson
import com.loveadvisor.core.model.*

/** Persisted through the Android encrypted document store. Names never identify a contact alone. */
class RecentConversations {
    var entries:List<ChatSnapshot> = emptyList(); private set
    var lastCleanup:Long=0; private set
    fun remember(snapshot:ChatSnapshot) {
        require(snapshot.identity?.kind==ChatKind.DIRECT)
        entries=entries.filterNot { it.packageName==snapshot.packageName && it.identity?.id==snapshot.identity!!.id }+snapshot.copy(messages=snapshot.messages.takeLast(50))
    }
    fun cleanup(now:Long) { if(now-lastCleanup>=12*3600000L || lastCleanup==0L) { entries=entries.map { it.copy(messages=it.messages.takeLast(50)) }; lastCleanup=now } }
    fun match(visible:ChatSnapshot):ChatSnapshot? {
        val messages=visible.messages
        if(messages.size<3 || messages.map { it.speaker }.distinct().size<2) return null
        fun equal(a:ChatMessage,b:ChatMessage)=a.speaker==b.speaker && a.text==b.text
        val matches=entries.filter { entry ->
            if(entry.packageName!=visible.packageName || entry.identity?.displayTitle!=visible.identity?.displayTitle) false else {
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
    companion object { fun decode(raw:String):RecentConversations=runCatching { Gson().fromJson(raw,RecentConversations::class.java).apply { entries=entries.filter { it.identity?.kind==ChatKind.DIRECT }.map { it.copy(messages=it.messages.takeLast(50)) } } }.getOrDefault(RecentConversations()) }
}
