package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.RequestTicket
data class BatchState(val current:VisibleBatch?=null,val loading:Boolean=false,val error:String?=null,val isFailure:Boolean=false)
data class RefreshRequest(val requestTicket:RequestTicket,val reanalyze:Boolean,val previous:List<String>,val analysis:AdvisorAnalysis?)
