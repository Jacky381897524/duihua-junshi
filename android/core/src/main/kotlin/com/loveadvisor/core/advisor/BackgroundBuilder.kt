package com.loveadvisor.core.advisor
import com.loveadvisor.core.memory.*
import com.loveadvisor.core.model.ChatMessage
import com.google.gson.GsonBuilder
object BackgroundBuilder {
    private val gson=GsonBuilder().disableHtmlEscaping().create()
    fun build(self:RelationshipProfile,target:RelationshipProfile?,memory:MemoryContext?,history:List<List<ChatMessage>>,background:String):String {
        var scale=1.0
        while(true) {
            fun text(value:String,limit:Int):String { var cut=value.take((limit*scale).toInt().coerceAtLeast(1)); if(cut.lastOrNull()?.isHighSurrogate()==true) cut=cut.dropLast(1); return cut }
            fun profile(p:RelationshipProfile,details:Int)=p.copy(id=text(p.id,80),nickname=text(p.nickname,80),details=text(p.details,details),mbti=p.mbti?.let { text(it,16) },goal=text(p.goal,160),tone=text(p.tone,100),boundaries=text(p.boundaries,200),stage=text(p.stage,60))
            fun items(values:List<MemoryItem>,limit:Int)=values.takeLast(limit).map { mapOf("kind" to it.kind.name,"text" to text(it.text,120),"inferred" to it.inferred,"recordedAt" to it.createdAt) }
            val data=linkedMapOf<String,Any?>(
                "用户背景" to text(background,1800),
                "已确认对象资料" to target?.let { profile(it,600) },
                "本人资料" to profile(self,350),
                "记忆资料" to memory?.let { mapOf("events" to items(it.events.filter { item -> System.currentTimeMillis()-item.createdAt<=ConversationTimePolicy.maxAgeMillis },5),"hypotheses" to items(it.hypotheses,3),"facts" to items(it.facts,3)) },
                "历史记录" to history.map { chat -> ConversationTimePolicy.currentMessages(com.loveadvisor.core.model.ChatSnapshot("history",-1,null,chat)) }.filter { it.isNotEmpty() }.takeLast(2).map { chat -> chat.takeLast(4).map { mapOf("speaker" to it.speaker.name,"sender" to it.sender?.let { name -> text(name,40) },"text" to text(it.text,120),"time" to it.time,"firstObservedAt" to it.observedAtMillis) } }
            )
            val json=gson.toJson(data)
            if(json.length<=6000) return json
            scale*=.7
        }
    }
}
