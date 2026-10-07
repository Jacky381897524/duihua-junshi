package com.loveadvisor.core.session
import com.loveadvisor.core.model.*

class RequestGate(private val tracker: SessionTracker) {
    private var generation = 0L
    private var active: RequestTicket? = null
    @Synchronized fun begin(token: SessionToken): RequestTicket {
        require(tracker.isCurrent(token)) { "会话已变化，请重新读取" }
        return RequestTicket(token, ++generation).also { active = it }
    }
    @Synchronized fun accept(ticket: RequestTicket): Boolean = ticket == active && tracker.isCurrent(ticket.session)
    @Synchronized fun cancel() { generation++; active = null }
}
