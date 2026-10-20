package com.loveadvisor.core.input

/** Each asynchronous callback keeps its attempt scope, even if another fill has started. */
class FillStageTrace(private val now:()->Long) {
    private var generation=0L
    private var startedAt=0L
    private val stages=ArrayDeque<String>()
    fun begin() { generation++;startedAt=now();stages.clear() }
    fun recorder():(String)->Unit {
        val attempt=generation
        return { stage ->
            if(attempt==generation) {
                if(stages.size>=12)stages.removeFirst()
                stages.addLast("${now()-startedAt}ms $stage")
            }
        }
    }
    fun description()=stages.joinToString(" → ").ifEmpty { "尚未点击填入" }
}
