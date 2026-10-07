package com.loveadvisor.core.memory
import java.util.UUID
interface MemoryPersistence { fun load():MemoryState; fun save(state:MemoryState) }
class MemoryRepository(private val persistence:MemoryPersistence,private val policy:MemoryPolicy=MemoryPolicy()) {
    fun state()=persistence.load()
    @Synchronized fun configure(enabled:Boolean,paused:Boolean) { val before=state(); persistence.save(before.copy(enabled=enabled,paused=paused,revision=before.revision+1)) }
    @Synchronized fun context(objectId:String):MemoryContext {
        val items=state().items.filter { it.objectId==objectId }
        return MemoryContext(items.filter { it.kind==MemoryKind.EVENT },items.filter { it.kind==MemoryKind.HYPOTHESIS },items.filter { it.kind==MemoryKind.FACT })
    }
    @Synchronized fun apply(update:MemoryUpdate):MemoryChange? {
        val before=state()
        if(!policy.validate(before,update).accepted) return null
        val merged=(before.items.filter { old -> update.items.none { it.id==old.id && it.objectId==old.objectId } }+update.items).distinctBy { it.objectId to it.id }
        val change=MemoryChange(UUID.randomUUID().toString(),update.objectId,before.items.filter { it.objectId==update.objectId },"新增／更新 ${update.items.size} 条记忆")
        persistence.save(before.copy(items=merged,undo=(before.undo+change).takeLast(20),revision=before.revision+1))
        return change
    }
    @Synchronized fun undo(changeId:String):Boolean {
        val before=state(); val change=before.undo.lastOrNull() ?: return false
        if(change.id!=changeId) return false
        persistence.save(before.copy(items=before.items.filter { it.objectId!=change.objectId }+change.before,undo=before.undo.dropLast(1),revision=before.revision+1))
        return true
    }
    @Synchronized fun forgetObject(objectId:String) {
        val before=state()
        persistence.save(before.copy(items=before.items.filter { it.objectId!=objectId },undo=before.undo.filter { it.objectId!=objectId },revision=before.revision+1))
    }
    @Synchronized fun clearAll() { val before=state(); persistence.save(before.copy(items=emptyList(),undo=emptyList(),revision=before.revision+1)) }
}
