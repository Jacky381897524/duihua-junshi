package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class MessageTimeParserTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val now=Instant.parse("2026-01-01T04:00:00Z").toEpochMilli() // Jan 1, 12:00 locally
    @Test fun relativeAndCalendarDatesResolveAtReadTimeAcrossYearBoundary() {
        assertEquals(Instant.parse("2025-12-30T10:00:00Z").toEpochMilli(),MessageTimeParser.parse("前天 18:00",now,zone).epochMillis)
        assertEquals(Instant.parse("2025-12-31T10:00:00Z").toEpochMilli(),MessageTimeParser.parse("12月31日 下午6:00",now,zone).epochMillis)
        assertEquals(Instant.parse("2025-12-31T15:59:59.999Z").toEpochMilli(),MessageTimeParser.parse("昨天",now,zone).epochMillis)
        assertEquals(Instant.parse("2025-12-30T10:30:00Z").toEpochMilli(),MessageTimeParser.parse("2025年12月30日 18:30",now,zone).epochMillis)
    }
    @Test fun bareClockWithoutADateCannotInventToday() {
        assertNull(MessageTimeParser.parse("10:30",now,zone).epochMillis)
        assertNull(MessageTimeParser.parse("2026年2月30日",now,zone).epochMillis)
        assertNull(MessageTimeParser.parse("明天出门",now,zone).epochMillis)
        assertFalse(MessageTimeParser.isSeparator("下午"))
    }
    @Test fun nativeClockInAnOldDateSectionDoesNotBecomeToday() {
        fun node(text:String,y:Int)=NodeRecord(null,text,"TextView",false,RectI(380,y,620,y+30))
        val time=MessageTimeParser.before(listOf(node("前天 09:00",200),node("10:00",300)),RectI(130,400,600,460),RectI(0,0,1000,2000),now,zone)
        assertEquals("10:00",time?.label)
        assertEquals(Instant.parse("2025-12-30T02:00:00Z").toEpochMilli(),time?.epochMillis)
    }
}
