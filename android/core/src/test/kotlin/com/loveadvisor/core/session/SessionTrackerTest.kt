package com.loveadvisor.core.session
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SessionTrackerTest {
    @Test fun quoteOnlyChangesInvalidateCandidatesBuiltForDifferentContext() {
        val t=SessionTracker();val screen=chat()
        val old=t.update(screen.copy(messages=screen.messages.map { it.copy(quote=ChatQuote("前文甲","小雨")) }))!!
        val updated=t.update(screen.copy(messages=screen.messages.map { it.copy(quote=ChatQuote("前文乙","小雨")) }))!!
        assertNotEquals(old.revision,updated.revision);assertFalse(t.isCurrent(old))
    }
    private fun chat(id: String = "contact-a", title: String? = "小雨", text: String = "今天怎么样", confirmed: Boolean = true, window: Int = 1) = ChatSnapshot("com.tencent.mm", window, ConversationIdentity(id, title, ChatKind.DIRECT, confirmed), listOf(ChatMessage("m1", Speaker.OTHER, null, text)))
    @Test fun confirmedConversationCanBeCurrent() {
        val tracker = SessionTracker()
        val token = tracker.update(chat())
        assertNotNull(token)
        assertTrue(tracker.isCurrent(token!!))
        assertEquals(token, tracker.update(chat()))
    }
    @Test fun sameNicknameNeedsSeparateIdentity() {
        val t = SessionTracker(); val old = t.update(chat())!!
        val new = t.update(chat(id="contact-b"))!!
        assertNotEquals(old.conversationId, new.conversationId)
        assertFalse(t.isCurrent(old)); assertTrue(t.isCurrent(new))
    }
    @Test fun unknownTitleIsNotPreviousContact() {
        val t = SessionTracker(); val old = t.update(chat())!!
        assertNull(t.update(chat(id="unknown",title=null,confirmed=false)))
        assertFalse(t.isCurrent(old))
    }
    @Test fun messageChangeInvalidatesOldReply() {
        val t=SessionTracker(); val old=t.update(chat())!!
        val new=t.update(chat(text="我们明天再聊"))!!
        assertFalse(t.isCurrent(old)); assertNotEquals(old.revision,new.revision)
    }
    @Test fun listPageLockAndDestroyInvalidate() {
        for(reason in listOf("list-page","lock","destroy")) {
            val t=SessionTracker(); val old=t.update(chat())!!
            t.invalidate(reason); assertFalse(t.isCurrent(old))
        }
    }
    @Test fun lateResponseCannotCrossEpochOrGeneration() {
        val t=SessionTracker(); val token=t.update(chat())!!; val g=RequestGate(t)
        val first=g.begin(token); assertTrue(g.accept(first))
        val next=g.begin(token); assertFalse(g.accept(first)); assertTrue(g.accept(next))
        t.update(chat(window=2)); assertFalse(g.accept(next))
    }
    @Test fun cancelRejectsEvenSameSessionResponse() {
        val t=SessionTracker(); val g=RequestGate(t); val r=g.begin(t.update(chat())!!)
        g.cancel(); assertFalse(g.accept(r))
    }
}
