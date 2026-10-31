package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*

object ConversationContextPolicy {
    /** A unique bidirectional passage may occur anywhere in either viewport, including before new messages. */
    fun strongOverlap(first:List<ChatMessage>,second:List<ChatMessage>):Boolean {
        for(i in first.indices)for(j in second.indices) {
            var n=0;var chars=0;val texts=mutableSetOf<String>();val speakers=mutableSetOf<Speaker>()
            while(i+n<first.size && j+n<second.size && same(first[i+n],second[j+n])) {
                val row=first[i+n];n++;chars+=row.text.length;texts.add(row.text);speakers.add(row.speaker)
                if(n>=3 && chars>=30 && texts.size>=3 && Speaker.ME in speakers && Speaker.OTHER in speakers)return true
            }
        }
        return false
    }
    fun limit(kind:ChatKind,groupLimit:Int=100)=if(kind==ChatKind.GROUP)groupLimit.takeIf { it in setOf(50,100,200) } ?: 100 else 50
    fun forSnapshot(snapshot:ChatSnapshot)=limit(snapshot.identity?.kind ?: ChatKind.UNKNOWN,snapshot.contextLimit)
    fun same(a:ChatMessage,b:ChatMessage):Boolean {
        val first=a.time?.epochMillis;val second=b.time?.epochMillis
        val sameTime=first==null || second==null || if(a.time.dateOnly || b.time.dateOnly) {
            val zone=java.time.ZoneId.systemDefault()
            java.time.Instant.ofEpochMilli(first).atZone(zone).toLocalDate()==java.time.Instant.ofEpochMilli(second).atZone(zone).toLocalDate()
        } else first==second
        return sameTime && a.speaker==b.speaker && a.sender==b.sender && a.text==b.text && a.quote==b.quote && a.kind==b.kind
    }
}
