package com.loveadvisor.core.memory

import com.loveadvisor.core.advisor.ImportParser
import com.loveadvisor.core.capture.MessageTimeParser
import com.loveadvisor.core.model.*
import java.time.ZoneId

object PersonalImport {
    /** Speaker labels are mandatory. Printed dates carry forward; an unknown date stays unknown. */
    fun parse(text:String,platform:String,nickname:String,group:Boolean=false,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):ChatSnapshot {
        require(text.toByteArray(Charsets.UTF_8).size<=1024*1024) { "单次导入最多1MiB" }
        require(nickname.isNotBlank() && nickname.length<=512)
        var time:MessageTime?=null
        val messages=ArrayList<ChatMessage>()
        for(line in text.lineSequence().filter { it.isNotBlank() }) {
            if(MessageTimeParser.isSeparator(line)) { time=MessageTimeParser.parse(line,now,zone,time);continue }
            val parsed=ImportParser().parseUtf8(line)
            require(parsed.speakerMappingConfirmed) { "每行请标明 我：/对方：，群聊请用成员昵称：；时间单独一行" }
            val row=parsed.messages.single()
            require(!group || row.speaker!=Speaker.OTHER || !row.sender.isNullOrBlank()) { "群聊必须标明每位成员昵称，不能统称对方" }
            messages.add(row.copy(id="personal-import-${messages.size}",time=time,observedAtMillis=now))
        }
        require(messages.isNotEmpty()) { "没有可导入消息" }
        return ChatSnapshot(platform,-1,ConversationIdentity(PersonalIdentity.key(platform,nickname),nickname,if(group)ChatKind.GROUP else ChatKind.DIRECT,true),messages,source=CaptureSource.IMPORT,readAtMillis=now)
    }
}
