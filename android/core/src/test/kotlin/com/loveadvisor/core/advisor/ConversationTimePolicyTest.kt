package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test

class ConversationTimePolicyTest {
    private val now=System.currentTimeMillis()
    private fun row(id:String,hoursAgo:Long?)=ChatMessage(id,Speaker.OTHER,null,"消息$id",time=hoursAgo?.let { MessageTime("可见时间",now-it*3600000) })
    private fun snapshot(vararg rows:ChatMessage)=AdvisorFixtures.snapshot().copy(messages=rows.toList(),readAtMillis=now)
    @Test fun cutoffDoesNotDeleteSavedMessagesAndExplicitOldTargetsKeepAllReadContext() {
        val read=snapshot(row("old",48),row("current",1))
        assertEquals(listOf("current"),ConversationTimePolicy.forReply(read,now=now).messages.map { it.id })
        assertEquals(listOf("old","current"),ConversationTimePolicy.forReply(read,ReplyTarget("old"),now).messages.map { it.id })
        assertEquals(2,read.messages.size)
        assertEquals("old",ConversationTurn.data(read,ReplyTarget("old"),now)["targetMessageId"])
        assertEquals("true",ConversationTurn.data(read,ReplyTarget("old"),now)["historicalTarget"])
    }
    @Test fun unknownTimesRemainUnknownAndTwentyFourHourBoundaryIsInclusive() {
        val read=snapshot(row("prefix",null),row("expired",25),row("boundary",24),row("recent",1),row("unknown",null))
        assertEquals(listOf("boundary","recent","unknown"),ConversationTimePolicy.currentMessages(read,now).map { it.id })
        assertEquals(listOf("unknown"),ConversationTimePolicy.currentMessages(snapshot(row("unknown",null)),now).map { it.id })
    }
    @Test fun twoDayOldReadIsHistoricalEvenWhenPrintedMessageTimesWereUnknown() {
        val read=snapshot(row("unknown",null)).copy(readAtMillis=now-48*3600000)
        assertTrue(ConversationTimePolicy.currentMessages(read,now).isEmpty())
        assertNull(read.messages.single().time)
    }
    @Test fun selectingANewMessageDoesNotBringExpiredTopicsBackIntoJudgment() {
        val read=snapshot(row("old",48),row("current",1))
        assertEquals(listOf("current"),ConversationTimePolicy.forReply(read,ReplyTarget("current"),now).messages.map { it.id })
        assertEquals(listOf("current"),com.loveadvisor.core.analysis.MessageJudgmentService.context(read,"current").messages.map { it.id })
    }
    @Test fun historicalReadFailureHasItsOwnDiagnosticReason() {
        val read=snapshot(row("old",48))
        val failure=runCatching { ConversationTimePolicy.forReply(read,now=now) }.exceptionOrNull()!!
        assertEquals(com.loveadvisor.core.diagnostics.LogReason.TOPIC_EXPIRED,com.loveadvisor.core.diagnostics.LogFault.capture(failure).reason)
    }
    @Test fun explicitTargetScopeChangesWhenTheSameMessageBecomesHistorical() {
        val read=snapshot(row("target",1))
        assertNotEquals(ConversationTimePolicy.scopeKey(read,ReplyTarget("target"),now),ConversationTimePolicy.scopeKey(read,ReplyTarget("target"),now+48*3600000))
    }
    @Test fun timeMetadataSurvivesStorageAndLegacyRowsKeepUnknownTimes() {
        val read=snapshot(row("old",48).copy(observedAtMillis=now-47*3600000),row("current",1))
        val stored=RecentConversations().apply { remember(read) }
        val restored=RecentConversations.decode(stored.encode()).entries.single()
        assertEquals(read,restored)
        assertEquals(listOf("current"),ConversationTimePolicy.currentMessages(restored,now).map { it.id })
        val json=com.google.gson.Gson().toJsonTree(stored).asJsonObject
        json["entries"].asJsonArray.forEach { entry -> entry.asJsonObject.remove("readAtMillis");entry.asJsonObject["messages"].asJsonArray.forEach { it.asJsonObject.remove("time");it.asJsonObject.remove("observedAtMillis") } }
        val legacy=RecentConversations.decode(json.toString()).entries.single()
        assertTrue(legacy.messages.all { it.time==null && it.observedAtMillis==0L })
        assertEquals(2,ConversationTimePolicy.currentMessages(legacy,now).size)
    }
    @Test fun rereadWithoutDateCannotErasePreviouslyKnownAge() {
        val read=snapshot(row("old",48))
        val conversation=AutomaticConversation()
        conversation.observe(read,now,true)
        conversation.observe(read.copy(messages=listOf(read.messages.single().copy(id="new-id",time=null))),now+60000,false)
        assertTrue(ConversationTimePolicy.expired(conversation.current!!.messages.single(),now))
    }
    @Test fun sameWordsOnDifferentDatesDoNotMergeIntoTheSameMessage() {
        val old=row("a",48)
        assertFalse(ConversationContextPolicy.same(old,old.copy(id="b",time=MessageTime("今天",now))))
    }
    @Test fun expiredScopeDoesNotReuseOldCommonAnalysisOrReplyHistory() {
        val tracker=SessionTracker();val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
        val read=snapshot(row("first",23),row("last",1))
        val pending=coordinator.begin(read)!!
        assertTrue(coordinator.complete(pending.requestTicket,AdvisorFixtures.valid()))
        // Only timestamps change: that changes which material may inform a new judgment.
        val later=read.copy(messages=listOf(row("first",49),row("last",1)))
        val next=coordinator.begin(later)!!
        assertNull(next.analysis)
        assertTrue(next.previous.isEmpty())
    }
}
