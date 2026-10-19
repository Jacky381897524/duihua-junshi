package com.loveadvisor.core.input
import com.loveadvisor.core.advisor.AdvisorFixtures
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.*
import org.junit.Assert.*
import org.junit.Test
class FillGuardTest {
    private val tracker=SessionTracker(); private val guard=FillGuard(tracker)
    private val chat=AdvisorFixtures.snapshot(); private val input=NodeRecord("input","草稿","android.widget.EditText",true,RectI(120,1700,760,1820))
    private val title=NodeRecord("title",chat.identity!!.displayTitle,"TextView",false,RectI(420,80,580,140))
    private val w=WindowSnapshot(chat.packageName,chat.windowId,RectI(0,0,1000,2000),listOf(title,input),chat.identity)
    @Test fun anInjectedCachedIdentityCannotReplaceAFreshTitleAfterASameWindowContactSwitch() {
        val token=tracker.update(chat)!!
        assertFalse(guard.verify(token,chat,w.copy(nodes=listOf(title.copy(text="另一个对象"),input)),input).allowed)
    }
    @Test fun missingTitleCannotAuthorizeNativeFillEvenWhenBodyAndCachedIdentityMatch() {
        val token=tracker.update(chat)!!
        assertFalse(guard.verify(token,chat,w.copy(nodes=listOf(input)),input).allowed)
    }
    @Test fun currentConfirmedBottomInputIsAllowed() { val t=tracker.update(chat)!!; assertTrue(guard.verify(t,chat,w,input).allowed) }
    @Test fun changedMessagesAndSameNameDifferentIdentityAreRejected() {
        val t=tracker.update(chat)!!; tracker.update(chat.copy(identity=chat.identity!!.copy(id="b"))); assertFalse(guard.verify(t,chat,w,input).allowed)
    }
    @Test fun searchUpperEditableAndUnconfirmedWindowAreRejected() {
        val t=tracker.update(chat)!!
        assertFalse(guard.verify(t,chat,w,input.copy(text="搜索")).allowed)
        assertFalse(guard.verify(t,chat,w,input.copy(bounds=RectI(100,100,900,200))).allowed)
        assertFalse(guard.verify(t,chat,w.copy(confirmedIdentity=null),input).allowed)
    }
}
