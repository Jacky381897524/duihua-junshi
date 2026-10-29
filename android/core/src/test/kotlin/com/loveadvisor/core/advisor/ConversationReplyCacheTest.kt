package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test

class ConversationReplyCacheTest {
    private fun saved(id:String,title:String=id)=SavedConversationReplies(AdvisorFixtures.snapshot().let { it.copy(identity=it.identity!!.copy(id=id,displayTitle=title)) },AdvisorFixtures.valid())
    @Test fun aToBToARetainsBothWithoutAnyCopyOrFill() {
        val cache=ConversationReplyCache();val a=saved("a");val b=saved("b")
        assertTrue(cache.remember(a));assertTrue(cache.remember(b))
        assertEquals(a,cache.find(a.snapshot.packageName,"a"));assertEquals(b,cache.find(b.snapshot.packageName,"b"))
        assertNull(cache.find("other.package","a"))
    }
    @Test fun ambiguousTitlesAreNotRestoredIntoAnUnprovenRecipient() {
        val cache=ConversationReplyCache();cache.remember(saved("a","同名"));cache.remember(saved("b","同名"))
        assertNull(cache.find("com.tencent.mm","同名"))
    }
    @Test fun latestSuccessfulBatchReplacesOnlyThatContact() {
        val cache=ConversationReplyCache();val a=saved("a");val b=saved("b")
        cache.remember(a);cache.remember(b)
        val next=a.copy(batch=a.batch.copy(replies=a.batch.replies.map { it.copy(text=it.text+"新的") }))
        cache.remember(next);assertEquals(2,cache.all().size);assertEquals(next,cache.find(a.snapshot.packageName,"a"));assertEquals(b,cache.find(b.snapshot.packageName,"b"))
        assertFalse(cache.remember(a.copy(batch=a.batch.copy(replies=emptyList()))));assertEquals(next,cache.find(a.snapshot.packageName,"a"))
    }
    @Test fun persistedResultsRestoreWithANewWindowTicketAndRejectOldAsyncResults() {
        val cache=ConversationReplyCache();val a=saved("a");cache.remember(a)
        val reload=ConversationReplyCache(object:ConversationReplyPersistence {override fun load()=ConversationReplyCache.decode(com.google.gson.Gson().toJson(cache.all()));override fun save(entries:List<SavedConversationReplies>){} })
        val tracker=SessionTracker();val c=BatchCoordinator(tracker,RequestGate(tracker))
        val old=c.begin(a.snapshot)!!;c.invalidate()
        assertTrue(c.restore(reload.find(a.snapshot.packageName,"a")!!,99))
        assertEquals(99,c.state.current!!.ticket.session.windowId);assertTrue(tracker.isCurrent(c.state.current!!.ticket.session))
        assertFalse(tracker.isCurrent(old.requestTicket.session));assertFalse(c.complete(old.requestTicket,AdvisorFixtures.valid()))
        assertEquals(a.batch,c.state.current!!.batch);assertFalse(c.state.loading)
    }
    @Test fun partialPersistedEntriesDoNotBlockValidContactsAndPasteDoesNotOverwriteThem() {
        val a=saved("a")
        val values=ConversationReplyCache.decode("[null,{},"+com.google.gson.Gson().toJson(a)+"]")
        val cache=ConversationReplyCache(object:ConversationReplyPersistence { override fun load()=values;override fun save(entries:List<SavedConversationReplies>){} })
        assertEquals(a,cache.find(a.snapshot.packageName,"a"))
        assertFalse(cache.remember(a.copy(snapshot=a.snapshot.copy(source=CaptureSource.PASTE))))
        assertEquals(1,cache.all().size)
    }
}
