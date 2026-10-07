package com.loveadvisor.core.input
import com.loveadvisor.core.advisor.AdvisorFixtures
import com.loveadvisor.core.model.*
import com.loveadvisor.core.capture.WindowSnapshot
import com.loveadvisor.core.session.SessionTracker
import org.junit.Assert.*
import org.junit.Test
class FillTransactionTest {
    private val tracker=SessionTracker(); private var chat=AdvisorFixtures.snapshot(); private val token=tracker.update(chat)!!
    private var draft="原始草稿"; private var clipboard=""; private var rejectSet=false; private var switchOnFocus=false; private var wrongReadback=false
    private var writes=0
    private val platform=object:FillPlatform {
        override fun current():FillTarget { val edit=NodeRecord("input",if(wrongReadback) "无法验证" else draft,"EditText",true,RectI(100,1700,800,1800)); return FillTarget(chat,WindowSnapshot(chat.packageName,chat.windowId,RectI(0,0,1000,2000),listOf(edit),chat.identity),edit) }
        override fun focus(target:FillTarget):Boolean { if(switchOnFocus) { chat=chat.copy(identity=chat.identity!!.copy(id="new")); draft="新对象草稿"; tracker.update(chat) }; return true }
        override fun setText(target:FillTarget,text:String):Boolean { if(rejectSet) return false; writes++; draft=text; return true }
        override fun paste(target:FillTarget):Boolean { writes++; draft=clipboard; return true }
        override fun copy(text:String) { clipboard=text }
    }
    @Test fun writesAndVerifiesOnlyCurrentDraft() { val result=FillTransaction(FillGuard(tracker),platform).fill(token,"候选"); assertEquals(FillStatus.FILLED,result.status); assertEquals("候选",draft); assertEquals(1,writes) }
    @Test fun switchingDuringFocusDoesNotClearNewDraft() { switchOnFocus=true; val result=FillTransaction(FillGuard(tracker),platform).fill(token,"旧候选"); assertEquals(FillStatus.STALE,result.status); assertEquals("新对象草稿",draft); assertEquals(0,writes) }
    @Test fun setTextFailureUsesFreshGuardedPaste() { rejectSet=true; assertEquals(FillStatus.FILLED,FillTransaction(FillGuard(tracker),platform).fill(token,"候选").status); assertEquals("候选",draft); assertEquals("候选",clipboard) }
    @Test fun unverifiableReadbackReturnsCopyOnly() { wrongReadback=true; assertEquals(FillStatus.COPIED_ONLY,FillTransaction(FillGuard(tracker),platform).fill(token,"候选").status); assertEquals("候选",clipboard) }
}
