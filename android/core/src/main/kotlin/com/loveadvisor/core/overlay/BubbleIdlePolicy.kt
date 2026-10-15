package com.loveadvisor.core.overlay

/** Idle time is reset by user gestures, never by passive chat/window updates. */
class BubbleIdlePolicy {
    var hidden=false
        private set
    var deadlineAt:Long?=null
        private set

    fun resume(now:Long,seconds:Int) {
        if(!hidden && deadlineAt==null) deadlineAt=now+safeSeconds(seconds)*1000L
    }
    fun pause() { deadlineAt=null }
    fun interact(now:Long,seconds:Int) {
        hidden=false
        deadlineAt=now+safeSeconds(seconds)*1000L
    }
    fun hideIfDue(now:Long):Boolean {
        val deadline=deadlineAt ?: return false
        if(hidden || now<deadline) return false
        hidden=true;deadlineAt=null
        return true
    }
    /** True consumes the first tap: reveal the icon without opening the panel. */
    fun reveal(now:Long,seconds:Int):Boolean {
        if(!hidden) return false
        interact(now,seconds)
        return true
    }
    companion object {
        const val DEFAULT_SECONDS=15
        const val MAX_SECONDS=3600
        fun safeSeconds(value:Int)=value.takeIf { it in 1..MAX_SECONDS } ?: DEFAULT_SECONDS
    }
}
