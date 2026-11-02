package com.loveadvisor.core.capture

import com.loveadvisor.core.advisor.CachedHeaderProof

/** Unchanged local observations must not restart the visible restoration state machine. */
class HeaderObservationMemo {
    private data class Observation(val pkg:String,val window:Int,val proof:CachedHeaderProof,val title:String?,val contact:String?)
    private var last:Observation?=null
    fun changedHeader(pkg:String,window:Int,proof:CachedHeaderProof?):Boolean {
        val old=last ?: return false
        return proof!=null && old.pkg==pkg && old.window==window && old.proof.width==proof.width && old.proof.height==proof.height && old.proof.stamp!=proof.stamp
    }
    fun unchanged(pkg:String,window:Int,proof:CachedHeaderProof?,title:String?,contact:String?,ambiguous:Boolean):Boolean {
        val old=last ?: return false
        return proof!=null && old.pkg==pkg && old.window==window && old.title==title && old.contact==contact &&
            old.proof.stamp==proof.stamp && old.proof.width==proof.width && old.proof.height==proof.height &&
            (!ambiguous || old.proof.conversationStamp==proof.conversationStamp)
    }
    fun remember(pkg:String,window:Int,proof:CachedHeaderProof?,title:String?,contact:String?) {
        last=proof?.let { Observation(pkg,window,it,title,contact) }
    }
    fun clear() { last=null }
}
