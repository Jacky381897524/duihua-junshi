package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test

class PanelReplyRestorationTest {
    private val tracker=SessionTracker()
    private val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
    private val cache=ConversationReplyCache()
    private val restoration=PanelReplyRestoration(coordinator,tracker,cache)
    private fun generate(snapshot:ChatSnapshot=AdvisorFixtures.snapshot(),target:ReplyTarget?=null):VisibleBatch {
        val request=coordinator.begin(snapshot,target)!!
        assertTrue(coordinator.complete(request.requestTicket,AdvisorFixtures.valid()))
        cache.remember(SavedConversationReplies(snapshot,coordinator.state.current!!.batch,target))
        return coordinator.state.current!!
    }

    @Test fun reopeningDoesNotClearSixRepliesWhileLocalHeaderIsPending() {
        val visible=generate();val token=tracker.currentToken
        restoration.begin("com.tencent.mm",1)
        assertSame("展开面板不能先删除刚生成的候选",visible,coordinator.state.current)
        assertEquals(6,coordinator.state.current!!.batch.replies.size)
        assertEquals(token,tracker.currentToken)
    }

    @Test fun missingOrUnmatchedOcrKeepsCurrentRepliesAcrossRepeatedCollapseAndReopen() {
        val visible=generate();val snapshot=tracker.lastSnapshot
        repeat(12) { round ->
            val check=restoration.begin("com.tencent.mm",1)
            assertEquals(PanelReplyRestoration.Result.RETAINED,restoration.complete(check,if(round%2==0)null else "识别错的昵称",false))
            assertSame(visible,coordinator.state.current)
            assertEquals(snapshot,tracker.lastSnapshot)
            assertTrue(tracker.isCurrent(visible.ticket.session))
        }
        assertEquals(1,cache.all().size)
    }

    @Test fun matchingHeaderPreservesReplyTargetAndRefreshHistory() {
        val snapshot=AdvisorFixtures.snapshot();val target=ReplyTarget("m1")
        val visible=generate(snapshot,target)
        val missed=restoration.begin(snapshot.packageName,1)
        assertEquals(PanelReplyRestoration.Result.RETAINED,restoration.complete(missed,null,false))
        val unverified=coordinator.state.error
        val check=restoration.begin(snapshot.packageName,1)
        assertEquals(PanelReplyRestoration.Result.RETAINED,restoration.complete(check,"小雨",true))
        assertNotEquals("成功核对后不能仍显示上次未核对状态",unverified,coordinator.state.error)
        assertSame(visible,coordinator.state.current)
        assertEquals(target,coordinator.targetFor(snapshot))
        val next=coordinator.begin(snapshot,target)!!
        assertFalse(next.reanalyze)
        assertEquals(6,next.previous.size)
    }

    @Test fun confirmedOtherContactWithoutCacheClearsOnlyTheVisibleContact() {
        val a=generate();val check=restoration.begin("com.tencent.mm",1)
        assertEquals(PanelReplyRestoration.Result.UNAVAILABLE,restoration.complete(check,"小林",true))
        assertNull(coordinator.state.current);assertNull(tracker.currentToken)
        assertEquals(a.batch,cache.find("com.tencent.mm","小雨")!!.batch)
    }

    @Test fun aToBToARestoresEachBatchWithoutRegenerating() {
        val a=generate();val source=AdvisorFixtures.snapshot()
        val bSnapshot=source.copy(identity=source.identity!!.copy(id="b",displayTitle="小林"))
        val b=SavedConversationReplies(bSnapshot,AdvisorFixtures.valid().copy(replies=AdvisorFixtures.valid().replies.map { it.copy(text=it.text+"聊小林") }))
        cache.remember(b)
        val toB=restoration.begin("com.tencent.mm",1)
        assertEquals(PanelReplyRestoration.Result.RESTORED,restoration.complete(toB,"小林",true))
        assertEquals(b.batch,coordinator.state.current!!.batch)
        val toA=restoration.begin("com.tencent.mm",1)
        assertEquals(PanelReplyRestoration.Result.RESTORED,restoration.complete(toA,"小雨",true))
        assertEquals(a.batch,coordinator.state.current!!.batch)
        assertEquals(2,cache.all().size)
    }

    @Test fun anotherApplicationOrWindowCannotKeepTheOldFillTicket() {
        val a=generate()
        val check=restoration.begin("com.tencent.mobileqq",88)
        assertNull(coordinator.state.current)
        assertFalse(tracker.sameConversation(a.ticket.session))
        assertEquals(PanelReplyRestoration.Result.UNAVAILABLE,restoration.complete(check,null,false))
        assertNotNull(cache.find("com.tencent.mm","小雨"))
    }

    @Test fun lateHeaderCannotReplaceANewManualReadOrNewerHeaderCheck() {
        generate();val old=restoration.begin("com.tencent.mm",1)
        val latest=restoration.begin("com.tencent.mm",1)
        assertEquals(PanelReplyRestoration.Result.STALE,restoration.complete(old,"小林",true))
        assertEquals(PanelReplyRestoration.Result.RETAINED,restoration.complete(latest,"小雨",true))
        val interrupted=restoration.begin("com.tencent.mm",1)
        val newer=AdvisorFixtures.snapshot().copy(contentRevision=9)
        coordinator.begin(newer)
        assertEquals(PanelReplyRestoration.Result.STALE,restoration.complete(interrupted,"小林",true))
        assertEquals(newer,tracker.lastSnapshot);assertTrue(coordinator.state.loading)
    }

    @Test fun inMemoryRepliesSurviveEvenWithoutAPersistedEntry() {
        val snapshot=AdvisorFixtures.snapshot()
        val request=coordinator.begin(snapshot)!!
        coordinator.complete(request.requestTicket,AdvisorFixtures.valid())
        val visible=coordinator.state.current
        val check=restoration.begin("com.tencent.mm",1)
        assertEquals(PanelReplyRestoration.Result.RETAINED,restoration.complete(check,null,false))
        assertSame(visible,coordinator.state.current)
        assertTrue(cache.all().isEmpty())
    }

    @Test fun groupAndExpressionModesKeepTheExactGeneratedBatch() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP))for(mode in 0..2) {
            val base=AdvisorFixtures.snapshot()
            val snapshot=base.copy(identity=base.identity!!.copy(kind=kind))
            val baseBatch=AdvisorFixtures.valid()
            val batch=baseBatch.copy(kind=kind,naturalRepliesEnabled=mode==1,realisticRepliesEnabled=mode==2,
                replies=if(kind==ChatKind.GROUP)baseBatch.replies.map { it.copy(targetMessageId="m1",topic="今天的近况") } else baseBatch.replies)
            val request=coordinator.begin(snapshot)!!
            assertTrue(coordinator.complete(request.requestTicket,batch))
            val visible=coordinator.state.current
            val check=restoration.begin("com.tencent.mm",1)
            assertEquals(PanelReplyRestoration.Result.RETAINED,restoration.complete(check,null,false))
            assertSame(visible,coordinator.state.current)
            assertSame(batch,coordinator.state.current!!.batch)
            coordinator.invalidate()
        }
    }

    @Test fun retainingVisibleRepliesDoesNotAuthorizeFillIntoADifferentLiveTitle() {
        val visible=generate();val snapshot=tracker.lastSnapshot!!
        val check=restoration.begin("com.tencent.mm",1)
        assertEquals(PanelReplyRestoration.Result.RETAINED,restoration.complete(check,null,false))
        val input=NodeRecord("input","","android.widget.EditText",true,RectI(120,1700,760,1820))
        val title=NodeRecord("title","小雨","TextView",false,RectI(420,80,580,140))
        val live=com.loveadvisor.core.capture.WindowSnapshot("com.tencent.mm",1,RectI(0,0,1000,2000),listOf(title,input),snapshot.identity)
        val fill=com.loveadvisor.core.input.FillGuard(tracker,true)
        assertTrue(fill.verify(visible.ticket.session,snapshot,live,input).allowed)
        assertFalse(fill.verify(visible.ticket.session,snapshot,live.copy(nodes=listOf(title.copy(text="小林"),input)),input).allowed)
        assertFalse(fill.verify(visible.ticket.session,snapshot,live.copy(nodes=listOf(input)),input).allowed)
        assertSame(visible,coordinator.state.current)
    }
}
