package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test

class ConversationReplyCacheTest {
    private fun saved(id:String,title:String=id)=SavedConversationReplies(AdvisorFixtures.snapshot().let { it.copy(identity=it.identity!!.copy(id=id,displayTitle=title)) },AdvisorFixtures.valid())
    @Test fun uniqueMatchingHeaderProofRestoresOtherContactAndSurvivesSerialization() {
        val cache=ConversationReplyCache();val a=saved("a");val b=saved("b")
        val proof=CachedHeaderProof("a".repeat(64),1080,2344)
        cache.remember(a);cache.remember(b.copy(headerProof=proof))
        assertEquals(b.batch,cache.findByHeaderProof("com.tencent.mm",proof)!!.batch)
        assertNull(cache.findByHeaderProof("other.package",proof))
        assertNull(cache.findByHeaderProof("com.tencent.mm",proof.copy(width=1000)))
        val reloaded=ConversationReplyCache(object:ConversationReplyPersistence {
            override fun load()=ConversationReplyCache.decode(com.google.gson.Gson().toJson(cache.all()))
            override fun save(entries:List<SavedConversationReplies>) {}
        })
        assertEquals("b",reloaded.findByHeaderProof("com.tencent.mm",proof)!!.snapshot.identity!!.id)
        reloaded.remember(b.copy(batch=b.batch));assertEquals(proof,reloaded.find("com.tencent.mm","b")!!.headerProof)
        reloaded.remember(a.copy(headerProof=proof));assertNull(reloaded.findByHeaderProof("com.tencent.mm",proof))
    }
    @Test fun proofDoesNotDisambiguateDifferentContactsWithSameDisplayTitle() {
        val cache=ConversationReplyCache();val proof=CachedHeaderProof("a".repeat(64),1080,2344)
        cache.remember(saved("a","同名").copy(headerProof=proof));cache.remember(saved("b","同名"))
        assertNull(cache.findByHeaderProof("com.tencent.mm",proof))
    }
    @Test fun repeatedTitleRequiresAUniqueMatchingConversationFingerprint() {
        val cache=ConversationReplyCache();val base=CachedHeaderProof("a".repeat(64),1080,2344)
        val first=base.copy(conversationStamp="b".repeat(64));val second=base.copy(conversationStamp="c".repeat(64))
        cache.remember(saved("a","同名").copy(headerProof=first));cache.remember(saved("b","同名").copy(headerProof=second))
        assertEquals("b",cache.findByHeaderProof("com.tencent.mm",second)!!.snapshot.identity!!.id)
        assertEquals("b",cache.findByHeaderProof("com.tencent.mm",second.copy(stamp="e".repeat(64)))!!.snapshot.identity!!.id)
        assertNull(cache.findByHeaderProof("com.tencent.mm",base))
        assertNull(cache.findByHeaderProof("com.tencent.mm",base.copy(conversationStamp="d".repeat(64))))
        cache.remember(saved("c","同名").copy(headerProof=second));assertNull(cache.findByHeaderProof("com.tencent.mm",second))
    }
    @Test fun bindingHeaderProofCannotTargetOtherIdentityOrInvalidFingerprint() {
        val cache=ConversationReplyCache();val item=saved("a");cache.remember(item)
        val proof=CachedHeaderProof("a".repeat(64),1080,2344)
        cache.bindHeaderProof(item.snapshot.copy(identity=item.snapshot.identity!!.copy(displayTitle="other")),proof)
        assertNull(cache.find("com.tencent.mm","a")!!.headerProof)
        cache.bindHeaderProof(item.snapshot,proof.copy(stamp="invalid"));assertNull(cache.find("com.tencent.mm","a")!!.headerProof)
        cache.bindHeaderProof(item.snapshot,proof);assertEquals(proof,cache.find("com.tencent.mm","a")!!.headerProof)
    }
    @Test fun defaultTenAndConfigurableLimitEvictLeastRecentlyUsed() {
        var maximum=10
        val cache=ConversationReplyCache(limit={ maximum })
        (1..10).forEach { cache.remember(saved("$it")) }
        cache.touch(cache.find("com.tencent.mm","1")!!)
        cache.remember(saved("11"))
        assertNull(cache.find("com.tencent.mm","2"));assertNotNull(cache.find("com.tencent.mm","1"));assertEquals(10,cache.all().size)
        maximum=20;(12..21).forEach { cache.remember(saved("$it")) };assertEquals(20,cache.all().size)
        maximum=3;cache.enforceLimit();assertEquals(3,cache.all().size)
    }
    @Test fun groupAndDirectConversationsShareTheSameCapacity() {
        val cache=ConversationReplyCache(limit={ 2 })
        val direct=saved("direct")
        val group=saved("group").let { entry ->
            entry.copy(snapshot=entry.snapshot.copy(identity=entry.snapshot.identity!!.copy(kind=ChatKind.GROUP)),
                batch=entry.batch.copy(kind=ChatKind.GROUP,replies=entry.batch.replies.map { it.copy(targetMessageId="m1",topic="近况") }))
        }
        assertTrue(cache.remember(direct));assertTrue(cache.remember(group));assertEquals(2,cache.all().size)
        cache.remember(saved("third"));assertNull(cache.find("com.tencent.mm","direct"));assertNotNull(cache.find("com.tencent.mm","group"))
    }
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
    @Test fun previousBatchRemainsUsableWhenNewGenerationFails() {
        val cache=ConversationReplyCache();val saved=saved("a");cache.remember(saved)
        val newer=saved.snapshot.copy(messages=saved.snapshot.messages+ChatMessage("new",Speaker.OTHER,null,"新的聊天内容"))
        assertNotNull(cache.findByIdentity(newer))
        assertNull(cache.findByIdentity(newer.copy(identity=newer.identity!!.copy(id="other"))))
        val tracker=SessionTracker();val coordinator=BatchCoordinator(tracker,RequestGate(tracker))
        assertTrue(coordinator.restore(cache.findByIdentity(newer)!!.copy(snapshot=newer),newer.windowId))
        val request=coordinator.begin(newer)!!;coordinator.fail(request.requestTicket,"服务商返回 402")
        assertEquals(saved.batch,coordinator.state.current!!.batch)
        assertTrue(tracker.isCurrent(coordinator.state.current!!.ticket.session))
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
