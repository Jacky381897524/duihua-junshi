package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*

class ExpiredConversationException:IllegalArgumentException("已读话题已超过24小时；旧消息仍保留，可选中旧消息“指定回复”，或手动读取当前聊天")

/** Filters only model input. The saved read and explicit historical targets remain available. */
object ConversationTimePolicy {
    const val maxAgeMillis=24*3600000L
    const val instruction="消息time仅来自该消息之前屏幕可见的聊天时间分隔标记，不是每条消息的发送时间；旧分隔标记之后仍可能有新消息，不能因此断定后续消息过期。readAtMillis和firstObservedAt是读取时间，不是发送时间。当前时间以evaluationTime为准。自动接话优先最近对话，不复活已结束或过期的话题，不把昨天/前天的活动、情绪或约定说成正在发生。时间不确定时保持未知，不猜日期。手动REPLY_TARGET允许回复历史消息：结合全部已读上下文，按实际时间自然重提或回顾，不能假装还在原时刻，也不能忽略后续已发生的回应。"
    fun expired(message:ChatMessage,now:Long=System.currentTimeMillis(),readAtMillis:Long=0):Boolean {
        // A prior separator is a lower bound, not an upper bound on a bubble's send time.
        // Only an actual observation bounds the send time from above. Conflicting
        // evidence is treated as unknown rather than blocking a potentially new reply.
        val observed=listOf(message.observedAtMillis,readAtMillis).filter { it>0 && it<=now }.minOrNull() ?: return false
        val separator=message.time?.epochMillis
        if(separator!=null && separator>observed)return false
        return now-observed>maxAgeMillis
    }
    fun currentMessages(snapshot:ChatSnapshot,now:Long=System.currentTimeMillis()):List<ChatMessage> {
        return snapshot.messages.filterIndexed { index,message ->
            if(expired(message,now,snapshot.readAtMillis))return@filterIndexed false
            // A subsequent, distinct separator bounds earlier messages from above.
            // Its date-only value is the end of the day, so it remains conservative.
            val start=message.time?.epochMillis
            val next=snapshot.messages.drop(index+1).firstNotNullOfOrNull { later ->
                later.time?.epochMillis?.takeIf { it>0 && it!=start }
            }
            val consistent=next!=null && (start==null || next>start)
            !consistent || next!!>now || now-next<=maxAgeMillis
        }
    }
    fun forReply(snapshot:ChatSnapshot,target:ReplyTarget?=null,now:Long=System.currentTimeMillis()):ChatSnapshot {
        val current=currentMessages(snapshot,now)
        if(target!=null) {
            require(snapshot.messages.count { it.id==target.messageId && it.speaker==Speaker.OTHER }==1) { "请选择已读的对方消息" }
            if(current.none { it.id==target.messageId })return snapshot
        }
        if(current.isEmpty())throw ExpiredConversationException()
        return snapshot.copy(messages=current)
    }
    fun scopeKey(snapshot:ChatSnapshot,target:ReplyTarget?,now:Long=System.currentTimeMillis()):String {
        val current=currentMessages(snapshot,now)
        val historical=target!=null && current.none { it.id==target.messageId }
        return (if(historical)"history:" else "current:")+(if(historical)snapshot.messages else current).joinToString("|") { it.id }
    }
    fun data(snapshot:ChatSnapshot,target:ReplyTarget?,now:Long):Map<String,String> {
        if(snapshot.readAtMillis<=0 && snapshot.messages.none { it.time!=null })return emptyMap()
        val selected=target?.let { t -> snapshot.messages.singleOrNull { it.id==t.messageId } }
        return mapOf("evaluationTime" to java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toString(),
            "readAtMillis" to snapshot.readAtMillis.toString(),"topicTimeWindowHours" to "24", "historicalTarget" to (selected!=null && currentMessages(snapshot,now).none { it.id==selected.id }).toString())
    }
}
