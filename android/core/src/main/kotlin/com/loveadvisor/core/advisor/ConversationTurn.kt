package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*

/** Role and next-turn contract shared by generation, ranking, refresh and details. */
object ConversationTurn {
    const val instruction="speaker=ME/from=me永远表示使用者本人（聊天右侧），OTHER/other永远表示对方（左侧）。这些身份是程序确定的数据，不能根据称呼、语气、内容或最后一条的位置改写。六条候选均以ME身份发送。responseMode=CONTINUE_SELF时，最后一句是我说的：结合完整对话，接着我最后一句自然补充、推进或延续，不能把这句话当对方的话来回答，也不能编造对方的新回应。REPLY_OTHER时回应对方最后的话。分析对方意图只能依据OTHER实际说过的话；背景、头像、引用和知识不是聊天消息。"
    fun data(snapshot:ChatSnapshot):Map<String,String> {
        require(snapshot.messages.isNotEmpty() && snapshot.messages.none { it.speaker==Speaker.UNKNOWN }) { "消息归属未明确，请重新读取" }
        val last=snapshot.messages.last()
        return mapOf("replyAs" to "ME","lastSpeaker" to last.speaker.name,"lastMessageId" to last.id,
            "responseMode" to if(last.speaker==Speaker.ME)"CONTINUE_SELF" else "REPLY_OTHER",
            "turnInstruction" to if(last.speaker==Speaker.ME)"六条均接着我最后一句继续说，分析完整上下文，不模拟对方回应" else "六条均以我身份回应对方，分析完整上下文")
    }
}
