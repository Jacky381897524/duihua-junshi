package com.loveadvisor.core.advisor

import com.loveadvisor.core.memory.*
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class HistoricalMemoryScopeTest {
    @Test fun legacyMemoryAndHistoryAlsoCannotReintroduceAnExpiredTopic() {
        val now=System.currentTimeMillis();val old=now-3*86400_000L
        val memory=MemoryContext(listOf(MemoryItem("event","p",MemoryKind.EVENT,"三天前吃火锅",createdAt=old)),emptyList(),listOf(MemoryItem("fact","p",MemoryKind.FACT,"不喜欢香菜",createdAt=old)))
        val rows=listOf(ChatMessage("old",Speaker.OTHER,null,"昨天吃火锅",time=MessageTime("三天前",old)),ChatMessage("new",Speaker.OTHER,null,"今天跑步了",time=MessageTime("今天",now)))
        val text=BackgroundBuilder.build(RelationshipProfile("self","本人"),RelationshipProfile("p","小林"),memory,listOf(rows),"")
        assertFalse(text.contains("火锅"));assertTrue(text.contains("香菜"));assertTrue(text.contains("跑步"));assertTrue(text.contains("time"))
    }
}
