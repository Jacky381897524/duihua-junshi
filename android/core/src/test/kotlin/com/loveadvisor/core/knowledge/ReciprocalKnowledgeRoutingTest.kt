package com.loveadvisor.core.knowledge

import org.junit.Assert.*
import org.junit.Test

class ReciprocalKnowledgeRoutingTest {
    private fun doc(id:String,tags:List<String>,text:String)=KnowledgeDocument(id,id,"knowledge/$id",tags,"original",text)

    @Test fun shortReplyAndUnevenEffortRetrieveReciprocityInsteadOfGenericExpression() {
        val docs=listOf(doc("generic",listOf("expression"),"## 普通\n接话"),doc("reciprocal",listOf("reciprocity"),"## 互惠\n不要单方面找话题"))
        for(text in listOf("总是我主动，她只回嗯","一味迎合太讨好了","对方敷衍，怎么有来有回")) {
            assertEquals("reciprocal",KnowledgeRouter().select(KnowledgeQuery(text),docs).first().id)
        }
    }

    @Test fun humorAndReciprocityExamplesAreReachableWithinUnifiedMultiDocumentBudget() {
        val docs=listOf(doc("jev-copilot.md",listOf("expression"),"## 事实\n以事实为准"),doc("romance-master.md",listOf("expression"),"## 幽默\n轻松接梗"),doc("generic",listOf("expression"),"## 普通\n接话"),doc("playbook",listOf("humor","reciprocity"),"## 接梗\n从共同场景留一个接话口\n"+"例子".repeat(2000)))
        val result=KnowledgeRouter().unified(KnowledgeQuery("怎么幽默接梗又不讨好"),docs)
        assertTrue(result.map { it.id }.containsAll(listOf("jev-copilot.md","romance-master.md","playbook")))
        assertTrue(result.size<=8 && result.sumOf { it.text.length }<=9000)
    }

    @Test fun unifiedFixedDocsUseActualSceneSoRefusalIsNotReplacedByInvitation() {
        val text="## 邀请聊天\n聊天见面邀请咖啡周末吃饭。\n\n## 明确拒绝\n不要联系，停止推进；尊重边界，不再追问。"
        val result=KnowledgeRouter().unified(KnowledgeQuery("她说不要联系"),listOf(doc("romance-master.md",listOf("invite","boundary"),text)))
        assertTrue(result.single().text.contains("停止推进"))
        assertFalse(result.single().text.contains("邀请咖啡"))
    }
}
