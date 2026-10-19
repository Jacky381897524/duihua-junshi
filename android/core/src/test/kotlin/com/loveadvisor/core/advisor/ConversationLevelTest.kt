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
}
