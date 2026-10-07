package com.loveadvisor.core.advisor
import com.loveadvisor.core.memory.*
import com.loveadvisor.core.model.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ConsultationContextTest {
    @Test fun refreshRetainsAnalyzedDraftAndTargetRatherThanMutableEditorContents() {
        val request=ConsultationRequest(AdvisorFixtures.snapshot(),"修复","最近有误会","我刚才说重了","target-1")
        assertTrue(request.combinedBackground().contains("我刚才说重了"))
        assertEquals(request.combinedBackground(),request.combinedBackground())
        assertEquals("target-1",request.objectId)
    }
    @Test fun hugeSelfProfileDoesNotCrowdOutConfirmedTargetAndBackground() {
        val self=RelationshipProfile("self","本人",details="很长的自述".repeat(50000))
        val target=RelationshipProfile("target-1","小雨",details="对方近期压力大",boundaries="尊重拒绝")
        val json=BackgroundBuilder.build(self,target,null,emptyList(),"用户待润色草稿：今天让你为难了")
        val data=JsonParser.parseString(json).asJsonObject
        assertTrue(json.length<=6000)
        assertEquals("target-1",data["已确认对象资料"].asJsonObject["id"].asString)
        assertTrue(data["用户背景"].asString.contains("今天让你为难了"))
        assertTrue(data["已确认对象资料"].asJsonObject["boundaries"].asString.contains("拒绝"))
    }
    @Test fun escapeHeavyContextRemainsValidBoundedJsonWithEveryContextCategory() {
        val p=RelationshipProfile("target-1","对象",details="\n\"\\\u0000".repeat(5000),goal="自然接话".repeat(500))
        val mem=MemoryItem("h","target-1",MemoryKind.HYPOTHESIS,"暂不确定".repeat(500),true)
        val history=listOf((1..80).map { ChatMessage("$it",Speaker.OTHER,null,"\n\"\\".repeat(500)) })
        val json=BackgroundBuilder.build(p.copy(id="self"),p,MemoryContext(listOf(mem),listOf(mem),listOf(mem)),history,"草稿："+"\n\"\\".repeat(5000))
        val data=JsonParser.parseString(json).asJsonObject
        assertTrue(json.length<=6000)
        assertTrue(data.has("记忆资料")); assertTrue(data.has("历史记录")); assertTrue(data.has("本人资料"))
        assertEquals("target-1",data["已确认对象资料"].asJsonObject["id"].asString)
    }
}
