package com.loveadvisor.core.memory
import org.junit.Assert.*
import org.junit.Test
class MemoryRevisionTest {
    @Test fun lateAutomaticUpdateCannotResurrectClearedOrManuallyEditedMemory() {
        val storage=object:MemoryPersistence { var value=MemoryState(enabled=true); override fun load()=value; override fun save(state:MemoryState) { value=state } }
        val repository=MemoryRepository(storage); val original=repository.state().revision
        val late=MemoryUpdate("a",listOf(MemoryItem("h","a",MemoryKind.HYPOTHESIS,"可能希望慢一点",true)),expectedRevision=original)
        repository.clearAll(); assertNull(repository.apply(late)); assertTrue(repository.state().items.isEmpty())
        val current=repository.state().revision
        repository.apply(MemoryUpdate("a",listOf(MemoryItem("f","a",MemoryKind.FACT,"用户已确认的事实")),false))
        assertNull(repository.apply(late.copy(expectedRevision=current))); assertEquals(listOf("用户已确认的事实"),repository.state().items.map { it.text })
    }
}
