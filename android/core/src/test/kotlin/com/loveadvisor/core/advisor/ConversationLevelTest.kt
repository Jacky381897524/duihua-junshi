package com.loveadvisor.core.advisor
import org.junit.Assert.*
import org.junit.Test
class ConversationLevelTest {
    @Test fun labelsAndUnknownAreHonest() {
        for(n in listOf(1,3))assertEquals("安全",ConversationLevel.from(n).label)
        for(n in listOf(4,6))assertEquals("一般",ConversationLevel.from(n).label)
        for(n in listOf(7,9))assertEquals("危险",ConversationLevel.from(n).label)
        for(n in listOf(null,0,10))assertEquals("待判断",ConversationLevel.from(n).label)
        assertNull(ConversationLevel.from(0).position)
        assertTrue((1..9).map { ConversationLevel.from(it).position!! }.zipWithNext().all { it.first<=it.second })
    }
    @Test fun sharedGradeTintKeepsUnknownNeutralAndEndpointsGreenRed() {
        val green=0xFF21884D.toInt();val amber=0xFFBF7B13.toInt();val red=0xFFD64040.toInt();val unknown=0xFF7E899B.toInt()
        assertEquals(green,ConversationLevel.from(1).tint(green,amber,red,unknown))
        assertEquals(red,ConversationLevel.from(9).tint(green,amber,red,unknown))
        assertEquals(amber,ConversationLevel.from(5).tint(green,amber,red,unknown))
        assertEquals(unknown,ConversationLevel.from(null).tint(green,amber,red,unknown))
    }
}
