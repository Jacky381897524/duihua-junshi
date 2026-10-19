package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class GroupReplyStyleTest {
    @Test fun groupNeverInheritsPrivateEvenWithSameTitle() {
        val snapshot=ChatSnapshot("wechat",1,ConversationIdentity("g","🔥",ChatKind.GROUP,true),emptyList())
        val key=ReplyStyles.key("wechat","🔥")!!
        val private=ReplyStyleCatalog(mapOf(key to "私聊温柔"),mapOf(key to "🔥"),mapOf(key to "wechat"))
        assertEquals("自然",GroupReplyStyle.resolve(snapshot,private,"私聊全局",ReplyStyleCatalog(),"自然"))
        assertEquals("群专属",GroupReplyStyle.resolve(snapshot,private,"私聊全局",ReplyStyleCatalog(mapOf(key to "群专属"),mapOf(key to "🔥"),mapOf(key to "wechat")),"自然"))
        assertEquals("私聊温柔",GroupReplyStyle.resolve(snapshot.copy(identity=snapshot.identity!!.copy(kind=ChatKind.DIRECT)),private,"私聊全局",ReplyStyleCatalog(),"群全局"))
    }
}
