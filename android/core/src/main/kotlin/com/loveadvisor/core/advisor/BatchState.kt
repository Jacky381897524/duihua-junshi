package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.RequestTicket
enum class IdentityCheckStatus { IDLE,CHECKING,VERIFIED,UNAVAILABLE }
data class BatchState(val current:VisibleBatch?=null,val loading:Boolean=false,val error:String?=null,val isFailure:Boolean=false,val identityChecking:Boolean=false,val identityStatus:IdentityCheckStatus=IdentityCheckStatus.IDLE)
enum class BatchCompletion { ACCEPTED,REJECTED,STALE }
data class RefreshRequest(val requestTicket:RequestTicket,val reanalyze:Boolean,val previous:List<String>,val analysis:AdvisorAnalysis?)
