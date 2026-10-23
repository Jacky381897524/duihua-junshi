package com.loveadvisor.core.analysis
import com.loveadvisor.core.model.*
data class DecisionOption(val id:String,val label:String,val weight:Double?)
data class MessageJudgment(val messageId:String,val literal:Double?,val intents:List<DecisionOption>,val needs:List<DecisionOption>,val actions:List<DecisionOption>,val level:Int?,val evidence:List<ChatMessage>,val source:String)
object MessageJudgmentService {
    fun context(snapshot:ChatSnapshot,targetId:String):ChatSnapshot {
        require(snapshot.messages.count { it.id==targetId }==1) { "目标消息已变化，请重新读取" }
        val at=snapshot.messages.indexOfFirst { it.id==targetId }
        require(snapshot.messages[at].speaker==Speaker.OTHER) { "请选择对方的消息" }
        return com.loveadvisor.core.advisor.ConversationTimePolicy.forReply(snapshot,ReplyTarget(targetId))
    }
}
