package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*

class ExpiredConversationException:IllegalArgumentException("已读话题已超过24小时；旧消息仍保留，可选中旧消息“指定回复”，或手动读取当前聊天")

/** Filters only model input. The saved read and explicit historical targets remain available. */
object ConversationTimePolicy {
    const val maxAgeMillis=24*3600000L
    const val instruction="消息time仅来自屏幕可见的聊天时间分隔标记，epochMillis是分段时间依据而非逐条精确发送时间；不能把readAtMillis读取时间当作消息发送时间。当前时间以evaluationTime为准。自动接话只分析提供的当前消息，不复活已结束或过期的话题，不把昨天/前天的活动、情绪或约定说成正在发生。未知时间保持未知，不猜日期。手动REPLY_TARGET允许回复历史消息：结合全部已读上下文，按实际时间自然重提或回顾，不能假装还在原时刻，也不能忽略后续已发生的回应。"
    fun expired(message:ChatMessage,now:Long=System.currentTimeMillis(),readAtMillis:Long=0):Boolean {
        // First observation bounds unknown send times from above; it is never presented as the send time.
        val latest=listOfNotNull(message.time?.epochMillis,message.observedAtMillis.takeIf { it>0 },readAtMillis.takeIf { it>0 }).filter { it>0 }.minOrNull()
        return latest!=null && latest<=now && now-latest>maxAgeMillis
    }
    fun currentMessages(snapshot:ChatSnapshot,now:Long=System.currentTimeMillis()):List<ChatMessage> {
        // An undated prefix before a proven expired section cannot be newer than that section.
        val lastExpired=snapshot.messages.indexOfLast { expired(it,now,snapshot.readAtMillis) }
        return snapshot.messages.filterIndexed { index,message -> index>lastExpired || !expired(message,now,snapshot.readAtMillis) && message.time?.epochMillis!=null }
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
