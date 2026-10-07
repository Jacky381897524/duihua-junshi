package com.loveadvisor.core.advisor
class ReplyHistory {
    private val batches=ArrayDeque<List<String>>()
    val previousTexts:List<String> get()=batches.flatMap { it }
    fun add(batch:AdvisorBatch) { batches.addLast(batch.replies.map { it.text }); while(batches.size>3) batches.removeFirst() }
    fun clear()=batches.clear()
}
