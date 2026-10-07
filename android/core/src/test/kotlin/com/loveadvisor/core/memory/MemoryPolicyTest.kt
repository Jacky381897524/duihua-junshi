package com.loveadvisor.core.memory
import org.junit.Assert.*
import org.junit.Test
class MemoryPolicyTest {
    private fun item(i:Int,kind:MemoryKind=MemoryKind.EVENT,owner:String="a",text:String="当次聊天事实")=MemoryItem("i$i",owner,kind,text)
    private fun update(items:List<MemoryItem>,automatic:Boolean=true)=MemoryUpdate("a",items,automatic)
    @Test fun disabledAndPausedDoNotAutoSave() {
        assertFalse(MemoryPolicy().validate(MemoryState(),update(listOf(item(1)))).accepted)
        assertFalse(MemoryPolicy().validate(MemoryState(enabled=true,paused=true),update(listOf(item(1)))).accepted)
    }
    @Test fun programEnforcesTotalAndObjectLimits() {
        val p=MemoryPolicy(); val enabled=MemoryState(enabled=true)
        assertTrue(p.validate(enabled,update(listOf(item(1)))).accepted)
        assertFalse(p.validate(enabled.copy(items=(1..20).map { item(it) }),update(listOf(item(21)))).accepted)
        assertFalse(p.validate(enabled.copy(items=(1..5).map { item(it,MemoryKind.HYPOTHESIS) }),update(listOf(item(6,MemoryKind.HYPOTHESIS)))).accepted)
        assertFalse(p.validate(enabled.copy(items=(1..200).map { item(it,MemoryKind.FACT,"owner-$it") }),update(listOf(item(201,MemoryKind.FACT)))).accepted)
        assertFalse(p.validate(enabled,update(listOf(item(1,text="字".repeat(201))))).accepted)
    }
    @Test fun inferredItemsNeverBecomeFacts() {
        val p=MemoryPolicy(); assertFalse(p.validate(MemoryState(enabled=true),update(listOf(item(1,MemoryKind.FACT).copy(inferred=true)))).accepted)
    }
    @Test fun objectsStayIsolatedAndUndoWorks() {
        val storage=object:MemoryPersistence { var data=MemoryState(enabled=true); override fun load()=data; override fun save(state:MemoryState) { data=state } }
        val repo=MemoryRepository(storage)
        val change=repo.apply(update(listOf(item(1))))
        assertNotNull(change); assertEquals(1,repo.context("a").events.size); assertTrue(repo.context("b").events.isEmpty())
        assertTrue(repo.undo(change!!.id)); assertTrue(repo.context("a").events.isEmpty())
    }
    @Test fun only20UndoChangesAndDeletionCannotResurrectObject() {
        val storage=object:MemoryPersistence { var data=MemoryState(enabled=true); override fun load()=data; override fun save(state:MemoryState) { data=state } }
        val repo=MemoryRepository(storage)
        for(i in 1..25) repo.apply(update(listOf(item(i,MemoryKind.FACT))))
        assertEquals(20,repo.state().undo.size)
        repo.forgetObject("a"); assertTrue(repo.context("a").facts.isEmpty()); assertTrue(repo.state().undo.isEmpty())
        repo.apply(update(listOf(item(30,MemoryKind.FACT)))); repo.clearAll(); assertTrue(repo.state().items.isEmpty())
    }
}
