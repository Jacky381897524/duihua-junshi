package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class ReplyStylesTest {
    @Test fun differentContactsStayIndependentAndClearingRestoresDefault() {
        val a=AdvisorFixtures.snapshot().copy(identity=ConversationIdentity("a","小雨",ChatKind.DIRECT,true))
        val b=a.copy(identity=ConversationIdentity("b","小林",ChatKind.DIRECT,true))
        val map=ReplyStyles.save(ReplyStyles.save(emptyMap(),"com.tencent.mm","小雨","没有标点符号"),"com.tencent.mm","小林","浪漫简短")
        assertEquals("没有标点符号",ReplyStyles.forSnapshot(map,a))
        assertEquals("浪漫简短",ReplyStyles.forSnapshot(map,b))
        val cleared=ReplyStyles.save(map,"com.tencent.mm","小雨","")
        assertEquals("",ReplyStyles.forSnapshot(cleared,a));assertEquals("浪漫简短",ReplyStyles.forSnapshot(cleared,b))
        assertEquals("",ReplyStyles.forSnapshot(map,a.copy(packageName="another.package")))
    }
}
