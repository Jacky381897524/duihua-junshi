package com.loveadvisor.core.memory
class MemoryPolicy {
    fun validate(state:MemoryState,update:MemoryUpdate):PolicyResult {
        if(update.expectedRevision!=null && update.expectedRevision!=state.revision) return PolicyResult(false,"记忆已被修改，忽略旧分析的自动写入")
        if(update.automatic && (!state.enabled || state.paused)) return PolicyResult(false,"长期记忆未启用或已暂停")
        if(update.items.isEmpty()) return PolicyResult(false,"没有可保存的记忆")
        if(update.items.any { it.objectId!=update.objectId || it.id.isBlank() || it.text.isBlank() }) return PolicyResult(false,"记忆对象或内容无效")
        if(update.items.any { it.text.codePointCount(0,it.text.length)>200 }) return PolicyResult(false,"每条记忆最多 200 字")
        if(update.items.any { it.inferred && it.kind!=MemoryKind.HYPOTHESIS }) return PolicyResult(false,"推测必须保存为暂定假设")
        val updated=(state.items.filter { old -> update.items.none { it.id==old.id && it.objectId==old.objectId } }+update.items).distinctBy { it.objectId to it.id }
        if(updated.size>200) return PolicyResult(false,"记忆总数已达 200 条，请先整理")
        if(updated.filter { it.objectId==update.objectId && it.kind==MemoryKind.EVENT }.size>20) return PolicyResult(false,"该对象最多保存 20 个事件")
        if(updated.filter { it.objectId==update.objectId && it.kind==MemoryKind.HYPOTHESIS }.size>5) return PolicyResult(false,"该对象最多保存 5 个暂定假设")
        return PolicyResult(true)
    }
}
