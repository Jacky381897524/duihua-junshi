package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*

object ConversationContextPolicy {
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
