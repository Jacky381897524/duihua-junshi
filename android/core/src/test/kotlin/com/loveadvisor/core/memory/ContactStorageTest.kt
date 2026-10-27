package com.loveadvisor.core.memory

import com.google.gson.Gson
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ContactStorageTest {
    private class Store:PersonalDocumentStore {
        val rows=linkedMapOf<String,String>();var failIndex=false;var failCleanup=false;var cleanupFailures=0
        override fun read(name:String)=rows[name]
        override fun write(name:String,text:String) { if(name=="personal-memory-index" && failIndex)error("disk");rows[name]=text }
        override fun remove(name:String) { if(failCleanup) { cleanupFailures++;error("cleanup") };rows.remove(name) }
        override fun names()=rows.keys.toSet()
    }
    private fun snapshot(name:String)=ChatSnapshot("com.tencent.mm",1,ConversationIdentity(name,name,ChatKind.DIRECT,true),listOf(ChatMessage("a",Speaker.ME,null,"周六还去跑步吗"),ChatMessage("b",Speaker.OTHER,null,"每周六都会去跑步")))
    @Test fun contactConversationIncludesBothSidesAndNoOtherContact() {
        val store=Store();val repo=RealityMemoryRepository(ContactMemoryPersistence(store));repo.ingest(snapshot("甲"),100);repo.ingest(snapshot("乙"),101)
        val data=repo.state().data;val contact=data.contacts.first()
        val rows=PersonalConversation.evidence(data,contact.id)
        assertEquals(setOf(Speaker.ME,Speaker.OTHER),rows.map { it.speaker }.toSet())
        assertTrue(rows.all { it.peerId==contact.id })
        assertEquals(repo.state(),RealityMemoryRepository(ContactMemoryPersistence(store)).state())
    }
    @Test fun migrationSeparatesObjectsAndFailedCommitKeepsThePreviousSnapshot() {
        val store=Store();val original=RealityMemoryState(data=PersonalData(contacts=listOf(PersonalContact("one","com.tencent.mm","甲"),PersonalContact("two","com.tencent.mm","乙"))))
        store.rows["personal-memory-v1"]=Gson().toJson(original)
        val persistence=ContactMemoryPersistence(store);assertEquals(original,persistence.load());persistence.save(original)
        assertNull(store.read("personal-memory-v1"));assertEquals(3,store.names().count { it.startsWith("pm-") })
        store.failIndex=true
        try { persistence.save(original.copy(revision=2,data=original.data.copy(contacts=original.data.contacts.drop(1))));fail() } catch(_:IllegalStateException) {}
        assertEquals(original,ContactMemoryPersistence(store).load())
    }
    @Test fun clearPurgesOldAndUndoShardsSoRawEvidenceDoesNotLinger() {
        val store=Store();val repo=RealityMemoryRepository(ContactMemoryPersistence(store));repo.ingest(snapshot("甲"),100);repo.clear()
        assertFalse(store.rows.values.any { it.contains("周六还去跑步") })
        assertTrue(RealityMemoryRepository(ContactMemoryPersistence(store)).state().data.evidence.isEmpty())
    }
    @Test fun cleanupFailureAfterAtomicCommitDoesNotLeaveMemoryAndDiskDisagreeing() {
        val store=Store();val repo=RealityMemoryRepository(ContactMemoryPersistence(store));repo.ingest(snapshot("甲"),100)
        repo.relationship(repo.contactId(snapshot("甲"))!!,"朋友")
        store.failCleanup=true
        repo.ingest(snapshot("乙"),101)
        assertEquals(repo.state(),RealityMemoryRepository(ContactMemoryPersistence(store)).state())
        assertTrue(store.cleanupFailures>0)
    }
}
