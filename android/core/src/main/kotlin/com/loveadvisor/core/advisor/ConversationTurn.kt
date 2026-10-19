package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*

/** Role and next-turn contract shared by generation, ranking, refresh and details. */
object ConversationTurn {
    const val instruction="speaker=ME/from=me永远表示使用者本人（聊天右侧），OTHER/other永远表示对方（左侧）。这些身份是程序确定的数据，不能根据称呼、语气、内容或最后一条的位置改写。六条候选均以ME身份发送。responseMode=CONTINUE_SELF时，最后一句是我说的：结合完整对话，接着lastMessageText自然补充、推进或延续，不能把这句话当对方的话来回答，也不能编造对方的新回应。REPLY_OTHER时回应对方最后的话。REPLY_TARGET时只围绕targetMessageId指定的已读消息，不能改答最后一条；targetMessageText和targetSender是目标原话与成员，后续实际发言仍作上下文，不编造回应。分析对方意图只能依据OTHER实际说过的话。quote是该条消息附带的前文引用，只作上下文，不是当前发言或新的最后一句；其author只是引用栏印出的名字，不能据此改写主消息speaker。语音没有屏幕转写时内容未知，不能编造音频意思；标明屏幕转写的文字与原语音是同一条发言。背景、头像和知识不是聊天消息。"
    fun data(snapshot:ChatSnapshot,target:ReplyTarget?=null):Map<String,String> {
        require(snapshot.messages.isNotEmpty() && snapshot.messages.none { it.speaker==Speaker.UNKNOWN }) { "消息归属未明确，请重新读取" }
        val last=snapshot.messages.last()
        if(target!=null) {
            val selected=snapshot.messages.singleOrNull { it.id==target.messageId && it.speaker==Speaker.OTHER }
            require(selected!=null) { "指定消息已变化，请重新读取" }
            return mapOf("replyAs" to "ME","lastSpeaker" to last.speaker.name,"lastMessageId" to last.id,"lastMessageText" to last.text.take(1200),"responseMode" to "REPLY_TARGET","targetMessageId" to selected.id,"targetMessageText" to selected.text.take(1200),"targetSender" to selected.sender.orEmpty(),"turnInstruction" to "六条均以ME身份围绕targetMessageId指定原话回答。完整已读对话只作上下文，目标不改为最新消息，不与我后续已说过的话矛盾或重复，不编造新的对方回应。")
        }
        return mapOf("replyAs" to "ME","lastSpeaker" to last.speaker.name,"lastMessageId" to last.id,
            "lastMessageText" to last.text.take(1200),"lastOtherMessageText" to snapshot.messages.lastOrNull { it.speaker==Speaker.OTHER }?.text.orEmpty().take(1200),
            "responseMode" to if(last.speaker==Speaker.ME)"CONTINUE_SELF" else "REPLY_OTHER",
            "turnInstruction" to if(last.speaker==Speaker.ME)"六条均接着我最后一句继续说，分析完整上下文，不模拟对方回应" else "六条均以我身份回应对方，分析完整上下文")
    }
}
