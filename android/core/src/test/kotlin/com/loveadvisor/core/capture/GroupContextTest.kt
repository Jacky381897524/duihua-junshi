package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class GroupContextTest {
    private fun group(vararg messages:ChatMessage)=ChatSnapshot("com.tencent.mm",42,ConversationIdentity("group-a","朋友群",ChatKind.GROUP,true),messages.toList())
    @Test fun groupConversationKeepsOneHundredReadMessagesByDefault() {
        val conversation=AutomaticConversation()
        val messages=(1..120).map { ChatMessage("m$it",Speaker.OTHER,if(it%2==0)"成员甲" else "成员乙","这是第${it}条实际消息") }
        conversation.observe(group(*messages.toTypedArray()),1,true)
        assertEquals(100,conversation.current!!.messages.size)
        assertEquals("m21",conversation.current!!.messages.first().id)
    }
    @Test fun sameWordsFromDifferentMembersDoNotEstablishContinuity() {
        val conversation=AutomaticConversation()
        conversation.restore(group(ChatMessage("a",Speaker.OTHER,"成员甲","这件事情我已经安排好明天下午三点了")))
        assertFalse(conversation.continues(group(ChatMessage("b",Speaker.OTHER,"成员乙","这件事情我已经安排好明天下午三点了"))))
    }
    @Test fun persistedGroupRetainsMemberNamesAndDoesNotMatchSameNamedDirectChat() {
        val messages=(1..4).map { ChatMessage("m$it",if(it%2==0)Speaker.ME else Speaker.OTHER,"成员甲","这是用于核对的第${it}条完整原文，今天的安排在下午三点钟") }
        val snapshot=group(*messages.toTypedArray())
        val store=RecentConversations();store.remember(snapshot)
        val reopened=RecentConversations.decode(store.encode())
        assertEquals("成员甲",reopened.entries.single().messages.first().sender)
        assertNull(reopened.match(snapshot.copy(identity=snapshot.identity!!.copy(kind=ChatKind.DIRECT))))
    }
    @Test fun duplicateGroupNamesNeverChooseAnAmbiguousHistory() {
        val messages=(1..4).map { ChatMessage("m$it",Speaker.OTHER,if(it%2==0)"甲" else "乙","这是第${it}条明确的原文，关于明天参加活动的具体安排和出发时间") }
        val first=group(*messages.toTypedArray())
        val store=RecentConversations();store.remember(first);store.remember(first.copy(identity=first.identity!!.copy(id="another-group")))
        assertNull(store.match(first))
    }
    @Test fun quoteAndMessageTypeSurviveRoundTripAndLegacyTextGetsDefaultType() {
        val quote=ChatQuote("明天几点出发","甲")
        val snapshot=group(ChatMessage("media",Speaker.OTHER,"乙","[表情包] 点头",quote,MessageKind.STICKER))
        val store=RecentConversations();store.remember(snapshot)
        val reopened=RecentConversations.decode(store.encode()).entries.single().messages.single()
        assertEquals(MessageKind.STICKER,reopened.kind);assertEquals(quote,reopened.quote)
        val old=store.encode().replace("\"kind\":\"STICKER\",","").replace(",\"kind\":\"STICKER\"","")
        assertEquals(MessageKind.TEXT,RecentConversations.decode(old).entries.single().messages.single().kind)
    }
}
