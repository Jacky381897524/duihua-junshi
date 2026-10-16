package com.loveadvisor.core.session
import com.loveadvisor.core.model.*
import java.security.MessageDigest

class SessionTracker {
    private var epoch = 0L
    private var revision = 0L
    private var signature: String? = null
    var currentToken: SessionToken? = null
        private set
    var lastSnapshot: ChatSnapshot? = null
        private set
    var lastInvalidation: String? = null
        private set

    @Synchronized fun update(snapshot: ChatSnapshot): SessionToken? {
        val identity = snapshot.identity
        if (identity?.confirmed != true || identity.id.isBlank() || snapshot.messages.isEmpty()) {
            invalidate("unconfirmed-or-empty")
            return null
        }
        val previous = currentToken
        val nextSignature = fingerprint(snapshot)
        val same = previous?.packageName == snapshot.packageName && previous.windowId == snapshot.windowId && previous.conversationId == identity.id
        if (!same) epoch++
        if (!same || nextSignature != signature) revision++
        currentToken = SessionToken(snapshot.packageName, snapshot.windowId, identity.id, revision, epoch)
        signature = nextSignature
        lastSnapshot = snapshot
        lastInvalidation = null
        return currentToken
    }

    @Synchronized fun invalidate(reason: String) {
        epoch++
        currentToken = null
        lastSnapshot = null
        signature = null
        lastInvalidation = reason
    }
    @Synchronized fun isCurrent(token: SessionToken): Boolean = token == currentToken
    @Synchronized fun sameConversation(token:SessionToken):Boolean=currentToken?.let { it.epoch==token.epoch && it.packageName==token.packageName && it.windowId==token.windowId && it.conversationId==token.conversationId }==true

    private fun fingerprint(snapshot: ChatSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (m in snapshot.messages) {
            for (part in listOf(m.speaker.name, m.sender.orEmpty(), m.text,m.quote?.author.orEmpty(),m.quote?.text.orEmpty())) {
                val data = part.toByteArray(Charsets.UTF_8)
                digest.update("${data.size}:".toByteArray(Charsets.UTF_8)); digest.update(data)
            }
        }
        digest.update(snapshot.contentRevision.toString().toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
