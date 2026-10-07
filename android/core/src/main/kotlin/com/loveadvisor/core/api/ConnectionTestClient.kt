package com.loveadvisor.core.api
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
data class ConnectionTestResult(val model:String,val detail:String)
class ConnectionTestClient(private val transport:HttpTransport) {
    fun reply(profile:ApiProfile,cancel:Cancellation):ConnectionTestResult {
        val answer=ChatCompletionClient(transport).complete(profile,listOf(PromptMessage("user","连接测试：请只回复“连接成功”。")),cancel,256)
        return ConnectionTestResult(profile.selectedModelId!!,"文字请求成功 · ${answer.take(60)}")
    }
    fun vision(profile:ApiProfile,image:String,cancel:Cancellation):ConnectionTestResult {
        val recognized=VisionClient(transport).read(profile,image,cancel)
        if(recognized.messages.map { it.speaker to it.text.trim() }!=listOf(Speaker.OTHER to "你好",Speaker.ME to "收到"))
            throw ApiException(message="图片请求已返回，但未准确识别测试气泡与左右归属")
        return ConnectionTestResult(profile.selectedModelId!!,"图片识别成功 · 2 条原文与左右归属正确")
    }
    fun judge(key:String,cancel:Cancellation):ConnectionTestResult {
        return judge(JevProfile(apiKey=key),cancel)
    }
    fun judge(profile:JevProfile,cancel:Cancellation):ConnectionTestResult {
        val snapshot=ChatSnapshot("com.tencent.mm",0,ConversationIdentity("connection-test","连接测试",ChatKind.DIRECT,true),listOf(ChatMessage("1",Speaker.OTHER,null,"你好")))
        val texts=listOf("你好，今天怎么样？","今天有什么新鲜事？","看到你来打招呼挺开心的。","专属听众上线了，你说。","想聊什么呢？","你先说，我听着。")
        val strategies=listOf(Strategy.ACKNOWLEDGE,Strategy.HUMOR,Strategy.APPRECIATION,Strategy.TEASING,Strategy.CLARIFY,Strategy.PACING)
        val replies=texts.mapIndexed { i,text -> ReplyPlan("test-$i",strategies[i],text,"","","",ReplyBranches("","","")) }
        JevJudgeClient(transport).rank(profile,snapshot,"内置连接测试，不是真实聊天",replies,cancel)
        return ConnectionTestResult(if(profile.custom)profile.model else ProviderPresets.JEV_MODEL,"判断请求成功 · 意图、风险和 6 条评分完整")
    }
}
