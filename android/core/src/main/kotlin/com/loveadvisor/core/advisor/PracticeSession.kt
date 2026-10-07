package com.loveadvisor.core.advisor
import com.loveadvisor.core.api.Cancellation
data class PracticeRequest(val generation:Long,val state:PracticeState,val cancel:Cancellation=Cancellation())
class PracticeSession {
    var state:PracticeState?=null; private set
    var loading=false; private set
    private var pending:PracticeRequest?=null
    private var generation=0L
    fun start(value:PracticeState) { cancel(); state=value }
    fun begin():PracticeRequest? { if(loading || state==null) return null; return PracticeRequest(generation,state!!).also { pending=it; loading=true } }
    fun complete(request:PracticeRequest,turn:PracticeTurn):Boolean { if(!accepts(request)) return false; state=turn.state; loading=false; pending=null; return true }
    fun fail(request:PracticeRequest):Boolean { if(!accepts(request)) return false; loading=false; pending=null; return true }
    private fun accepts(request:PracticeRequest)=pending===request && request.generation==generation && !request.cancel.cancelled
    fun cancel() { pending?.cancel?.cancel(); pending=null; loading=false; generation++ }
}
