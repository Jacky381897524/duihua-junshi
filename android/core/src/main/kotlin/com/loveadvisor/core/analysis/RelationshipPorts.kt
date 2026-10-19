package com.loveadvisor.core.analysis
import com.loveadvisor.core.model.*
import com.loveadvisor.core.api.Cancellation
data class EvidenceRef(val session:SessionToken,val messageId:String)
data class RelationshipEstimate(val valuePercent:Double?,val confidence:Double?,val evidence:List<EvidenceRef>) {
    init { require(valuePercent==null || valuePercent.isFinite() && valuePercent in 0.0..100.0);require(confidence==null || confidence.isFinite() && confidence in 0.0..1.0) }
}
interface RelationshipEstimator { fun estimate(snapshot:ChatSnapshot,cancel:Cancellation):RelationshipEstimate }
interface ConfirmedMemoryWriter { fun saveConfirmed(evidence:List<EvidenceRef>,text:String) }
