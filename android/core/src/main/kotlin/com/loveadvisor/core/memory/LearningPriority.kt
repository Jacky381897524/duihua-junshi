package com.loveadvisor.core.memory

import com.loveadvisor.core.api.Cancellation

/** Process-local foreground leases. A cancelled/late learning task can never become current again. */
class LearningPriority {
    class Ticket internal constructor(val cancel:Cancellation=Cancellation())
    private var foreground=0
    private var active:Ticket?=null
    @Synchronized fun canStart()=foreground==0 && active==null
    @Synchronized fun enterForeground():()->Unit {
        foreground++;cancel()
        var released=false
        return { synchronized(this) { if(!released) { released=true;foreground-- } } }
    }
    @Synchronized fun start():Ticket?=if(!canStart())null else Ticket().also { active=it }
    @Synchronized fun current(ticket:Ticket)=foreground==0 && active===ticket && !ticket.cancel.cancelled
    @Synchronized fun cancel() { active?.cancel?.cancel();active=null }
    @Synchronized fun finish(ticket:Ticket):Boolean { if(active!==ticket)return false;active=null;return true }
}
