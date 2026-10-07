package com.loveadvisor.core.advisor
import org.junit.Assert.*
import org.junit.Test
class GlobalReplyStylesTest {
    @Test fun dedicatedObjectStyleWinsAndOthersUseGlobalWithoutNameMatching() {
        val snapshot=AdvisorFixtures.snapshot()
        val styles=ReplyStyles.save(emptyMap(),snapshot.packageName,snapshot.identity!!.displayTitle!!,"温柔简短")
        assertEquals("温柔简短",ReplyStyles.forSnapshot(styles,snapshot,"没有标点符号"))
        val symbols=snapshot.copy(identity=snapshot.identity!!.copy(displayTitle="☆😊꧁༺꧂"))
        assertEquals("没有标点符号",ReplyStyles.forSnapshot(styles,symbols,"没有标点符号"))
        assertEquals("没有标点符号",ReplyStyles.forSnapshot(styles,snapshot.copy(identity=null),"没有标点符号"))
    }
    @Test fun clearingGlobalRestoresNormalButRetainsDedicatedObjectStyles() {
        val snapshot=AdvisorFixtures.snapshot()
        val styles=ReplyStyles.save(emptyMap(),snapshot.packageName,snapshot.identity!!.displayTitle!!,"浪漫")
        assertEquals("浪漫",ReplyStyles.forSnapshot(styles,snapshot,""))
        assertEquals("",ReplyStyles.forSnapshot(emptyMap(),snapshot,""))
        assertEquals(1500,ReplyStyles.forSnapshot(emptyMap(),snapshot,"x".repeat(2000)).length)
    }
}
