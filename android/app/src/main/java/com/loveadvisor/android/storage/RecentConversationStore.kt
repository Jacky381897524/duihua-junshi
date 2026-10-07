package com.loveadvisor.android.storage
import com.loveadvisor.core.capture.RecentConversations
import com.loveadvisor.core.model.ChatSnapshot
class RecentConversationStore(private val documents:AndroidDocumentStore) {
    private var data=documents.read("automatic-recent")?.let { RecentConversations.decode(it) } ?: RecentConversations()
    @Synchronized fun match(snapshot:ChatSnapshot)=data.match(snapshot)
    @Synchronized fun remember(snapshot:ChatSnapshot) { data.remember(snapshot); data.cleanup(System.currentTimeMillis()); save() }
    @Synchronized fun cleanup() { data.cleanup(System.currentTimeMillis()); save() }
    private fun save()=documents.write("automatic-recent",data.encode())
}
