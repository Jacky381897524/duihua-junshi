package com.loveadvisor.core.capture

/** Local window screenshots are rate limited; legacy display captures also need compositor time. */
object CaptureTiming {
    fun delay(lastStart:Long,now:Long,legacy:Boolean):Long=maxOf(if(legacy)80L else 0L,400L-(now-lastStart))
}
