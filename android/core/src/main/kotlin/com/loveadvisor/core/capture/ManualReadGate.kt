package com.loveadvisor.core.capture

/** One explicit screen-read gesture authorizes one pipeline; passive events create no tickets. */
class ManualReadGate {
    data class Ticket(val revision: Long)
    var active: Ticket? = null
        private set
    private var revision = 0L
    fun begin(): Ticket = Ticket(++revision).also { active = it }
    fun accepts(ticket: Ticket): Boolean = active == ticket
    fun finish(ticket: Ticket): Boolean {
        if (!accepts(ticket)) return false
        active = null
        return true
    }
    fun cancel() { active = null }
}
