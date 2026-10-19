package com.loveadvisor.core.analysis
import com.loveadvisor.core.model.SessionToken
data class JudgmentKey(val session:SessionToken,val targetMessageId:String,val judgeRevision:String)
class JudgmentRequestGate {
    private var generation=0L;private var key:JudgmentKey?=null
    @Synchronized fun begin(value:JudgmentKey):Long { key=value;return ++generation }
    @Synchronized fun accept(value:JudgmentKey,ticket:Long)=key==value && generation==ticket
    @Synchronized fun invalidate() { generation++;key=null }
}
