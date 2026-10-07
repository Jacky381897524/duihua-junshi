package com.loveadvisor.core.advisor
import com.google.gson.Gson
import com.loveadvisor.core.model.*
object AdvisorFixtures {
    fun valid():AdvisorBatch {
        val strategies=listOf(Strategy.ACKNOWLEDGE,Strategy.HUMOR,Strategy.APPRECIATION,Strategy.TEASING,Strategy.CLARIFY,Strategy.PACING)
        val texts=listOf("今天那件事后来怎么样了？","听起来今天挺有故事的，展开说说？","这件事你一直认真推进，挺不容易的。","今天的故事就差一个主角采访了，想听你讲讲。","我想确认下，你现在更想聊聊还是一起想办法？","你先休息一下，方便的时候再聊也可以。")
        return AdvisorBatch(AdvisorAnalysis(listOf("对方分享了今天的近况"),listOf("可能希望有人倾听"),listOf("还不知道完整经过"),"可能在分享经历",3,"先接住具体内容"),texts.mapIndexed { i,text -> ReplyPlan("r$i",strategies[i],text,"回应当前分享的近况，并给对方继续表达的空间","对方愿意继续交流时","需要继续观察对方反馈",ReplyBranches("根据对方实际回复接一个小问题","给一点空间，不连续追问","尊重拒绝并结束当前话题")) })
    }
    fun json(batch:AdvisorBatch=valid())=Gson().toJson(batch)
    fun snapshot()=ChatSnapshot("com.tencent.mm",1,ConversationIdentity("a","小雨",ChatKind.DIRECT,true),listOf(ChatMessage("m1",Speaker.OTHER,null,"今天遇到好多事")))
}
