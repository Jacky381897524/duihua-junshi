package com.loveadvisor.core.input
import com.loveadvisor.core.advisor.AdvisorFixtures
import com.loveadvisor.core.capture.WindowSnapshot
import com.loveadvisor.core.model.*
import com.loveadvisor.core.session.SessionTracker
import org.junit.Assert.*
import org.junit.Test
class AutomaticFillTest {
    @Test fun olderReplyCanFillNewRevisionButNeverANewContact() {
        val t=SessionTracker(); val old=AdvisorFixtures.snapshot(); val token=t.update(old)!!
        val next=old.copy(messages=old.messages+ChatMessage("2",Speaker.OTHER,null,"还有一件事")); t.update(next)
        val input=NodeRecord("input","","android.widget.EditText",true,RectI(120,1700,760,1820))
        val title=NodeRecord("title",next.identity!!.displayTitle,"TextView",false,RectI(420,80,580,140))
        val w=WindowSnapshot(next.packageName,next.windowId,RectI(0,0,1000,2000),listOf(title,input),next.identity)
        assertTrue(FillGuard(t,true).verify(token,next,w,input).allowed)
        assertFalse(FillGuard(t).verify(token,next,w,input).allowed)
        val other=next.copy(identity=next.identity!!.copy(id="another")); t.update(other)
        assertFalse(FillGuard(t,true).verify(token,other,w.copy(confirmedIdentity=other.identity),input).allowed)
    }
}
