package com.loveadvisor.core.capture
import com.loveadvisor.core.advisor.AdvisorFixtures
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class RecentConversationsTest {
    @Test fun identicalForwardedConversationOnAnotherAppDoesNotRestoreWechatHistory() {
        val store=RecentConversations()
        val snapshot=AdvisorFixtures.snapshot().copy(messages=(1..4).map { ChatMessage("$it",if(it%2==0)Speaker.ME else Speaker.OTHER,null,"独特聊天消息${it}，这里包含足够长的实际对话细节用于匹配上下文") })
        store.remember(snapshot)
        assertNull(store.match(snapshot.copy(packageName=ChatApps.QQ)))
    }
    @Test fun storesOnlyLatestFiftyAndKeepsThemAcrossTwelveHourCleanup() {
        val store=RecentConversations(); val base=AdvisorFixtures.snapshot()
        val rows=(1..70).map { ChatMessage("$it",if(it%2==0) Speaker.ME else Speaker.OTHER,null,"第${it}句有独特细节，请记住原文") }
        store.remember(base.copy(messages=rows)); store.cleanup(0); store.cleanup(13*3600000L)
        assertEquals(50,store.entries.single().messages.size); assertEquals("21",store.entries.single().messages.first().id)
        val restored=RecentConversations.decode(store.encode()); assertEquals(store.entries,restored.entries)
    }
    @Test fun titleAloneOrOneCommonMessageNeverRestoresAnotherContact() {
        val store=RecentConversations(); val base=AdvisorFixtures.snapshot(); store.remember(base)
        assertNull(store.match(base.copy(identity=base.identity!!.copy(id="new"))))
    }
    @Test fun uniqueLongBidirectionalOverlapCanRestoreRecentContext() {
        val store=RecentConversations(); val s=AdvisorFixtures.snapshot().copy(messages=(1..4).map { ChatMessage("$it",if(it%2==0) Speaker.ME else Speaker.OTHER,null,"独特聊天消息${it}，这里包含足够长的实际对话细节用于匹配上下文") })
        store.remember(s); assertEquals(s.identity!!.id,store.match(s.copy(identity=s.identity!!.copy(id="new")))?.identity?.id)
        store.remember(s.copy(identity=s.identity!!.copy(id="same-name-second"))); assertNull(store.match(s))
    }
}
