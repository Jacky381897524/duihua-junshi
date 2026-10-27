package com.loveadvisor.core.capture

import com.loveadvisor.core.advisor.AdvisorFixtures
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PastedConversationTest {
    @Test fun unreadPasteHasNoInventedRemoteIdentityOrFillPermission() {
        val s=PastedConversation.create("  Wanna grab coffee?  ","com.loveadvisor.android",now=1)
        assertFalse(ChatApps.supported(s.packageName));assertEquals(-1,s.windowId)
        assertEquals(CaptureSource.PASTE,s.source);assertEquals("Wanna grab coffee?",s.messages.single().text)
    }
    @Test fun verifiedDirectChatKeepsIdentityButDoesNotPretendPastedTextWasCaptured() {
        val original=AdvisorFixtures.snapshot();val s=PastedConversation.create("然后呢","com.loveadvisor.android",original,2)
        assertEquals(original.identity,s.identity);assertEquals(original.windowId,s.windowId)
        assertEquals(CaptureSource.PASTE,s.source);assertEquals(1,s.messages.size)
        assertEquals("今天遇到好多事",original.messages.single().text)
    }
    @Test fun groupPasteWithoutASpecificSenderIsNotBoundToAGroupMember() {
        val original=AdvisorFixtures.snapshot().copy(identity=ConversationIdentity("group","群",ChatKind.GROUP,true))
        assertFalse(ChatApps.supported(PastedConversation.create("你好","com.loveadvisor.android",original).packageName))
    }
    @Test fun emptyAndUnboundedPasteAreRejected() {
        for(text in listOf(" ","a".repeat(4001)))try { PastedConversation.create(text,"local");fail() } catch(_:IllegalArgumentException) {}
    }
}
