package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*

class AutomaticConversation {
    var current:ChatSnapshot?=null
        private set
    var updatedAt:Long=0
        private set

    fun restore(snapshot:ChatSnapshot) { current=snapshot.copy(messages=snapshot.messages.takeLast(50)) }
    fun continues(snapshot:ChatSnapshot):Boolean {
        if(current!=null && current?.packageName!=snapshot.packageName)return false
        val existing=current?.messages.orEmpty(); val visible=snapshot.messages
        if(existing.isEmpty()) return true
        fun same(a:ChatMessage,b:ChatMessage)=a.speaker==b.speaker && a.text==b.text
        if(existing.size==visible.size && existing.indices.all { same(existing[it],visible[it]) }) return true
        val overlap=(minOf(existing.size,visible.size) downTo 1).firstOrNull { n -> (0 until n).all { same(existing[existing.size-n+it],visible[it]) } } ?: 0
        if(overlap>0) {
            val rows=visible.take(overlap)
            if(existing.size==1 || rows.sumOf { it.text.length }>=8 && rows.map { it.text }.distinct().size>=2 || rows.any { it.text.length>=12 }) return true
        }
        return visible.size>=2 && visible.sumOf { it.text.length }>=8 && existing.size>=visible.size &&
            (0..existing.size-visible.size).any { start -> visible.indices.all { same(existing[start+it],visible[it]) } }
    }

    fun observe(snapshot:ChatSnapshot,now:Long,entered:Boolean):Boolean {
        val identity=requireNotNull(snapshot.identity)
        require(identity.kind==ChatKind.DIRECT && snapshot.messages.none { it.speaker==Speaker.UNKNOWN })
        val previous=current?.takeIf { it.packageName==snapshot.packageName && it.identity?.id==identity.id }
        val existing=previous?.messages.orEmpty()
        val visible=snapshot.messages
        fun same(a:ChatMessage,b:ChatMessage)=a.speaker==b.speaker && a.text==b.text
        val alreadyObserved=visible.isNotEmpty() && existing.size>=visible.size &&
            (0..existing.size-visible.size).any { start -> visible.indices.all { same(existing[start+it],visible[it]) } }
        val overlap=(minOf(existing.size,visible.size) downTo 1).firstOrNull { count ->
            (0 until count).all { same(existing[existing.size-count+it],visible[it]) }
        } ?: 0
        if(existing.isNotEmpty() && !alreadyObserved && overlap==0 && !entered) return false
        val added=if(alreadyObserved) emptyList() else visible.drop(overlap)
        current=snapshot.copy(messages=(existing+added).takeLast(50))
        updatedAt=now
        return current!!.messages.isNotEmpty() && (entered || added.isNotEmpty() && added.last().speaker==Speaker.OTHER)
    }

    fun clear() { current=null; updatedAt=0 }
}
