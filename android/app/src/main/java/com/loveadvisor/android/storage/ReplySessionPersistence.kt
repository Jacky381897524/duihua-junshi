package com.loveadvisor.android.storage

import com.loveadvisor.core.advisor.*
import com.google.gson.Gson
import java.util.concurrent.Executors

/** Encrypted local state. Ordered asynchronous writes keep disk work off the overlay thread. */
class ReplySessionPersistence(private val documents:AndroidDocumentStore):ConversationReplyPersistence {
    private val writer=Executors.newSingleThreadExecutor { r -> Thread(r,"reply-session-storage") }
    override fun load():List<SavedConversationReplies> = try {
        documents.read("reply-sessions")?.let(ConversationReplyCache::decode).orEmpty()
    } catch(error:Exception) { com.loveadvisor.android.diagnostics.DiagnosticLogs.failed(com.loveadvisor.core.diagnostics.LogStep.LOCAL_TASK,error);emptyList() }
    override fun save(entries:List<SavedConversationReplies>) { writer.execute {
        try {
            documents.write("reply-sessions",Gson().toJson(entries))
            com.loveadvisor.android.diagnostics.DiagnosticLogs.event(com.loveadvisor.core.diagnostics.LogStep.REPLY_CACHE_SAVE,metrics=mapOf(com.loveadvisor.core.diagnostics.LogMetric.COUNT to entries.size.toLong()))
        }
        catch(error:Exception) { com.loveadvisor.android.diagnostics.DiagnosticLogs.failed(com.loveadvisor.core.diagnostics.LogStep.REPLY_CACHE_SAVE,error) }
    } }
}
