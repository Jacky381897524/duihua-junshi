package com.loveadvisor.core.integration

import com.google.gson.Gson
import com.loveadvisor.core.capture.ChatApps
import com.loveadvisor.core.knowledge.*
import com.loveadvisor.core.memory.*
import org.junit.Assert.*
import org.junit.Test

class SocialUpgradeTest {
    @Test fun requestedSocialAppsCanEnterCaptureInsteadOfBeingRejectedAtTheRegistry() {
        for(pkg in listOf("cn.soulapp.android","com.xingin.xhs","com.twitter.android","com.p1.mobile.putong","com.immomo.momo","com.wepie.ivy","cn.xiaochuankeji.tieba","com.netease.cloudmusic","com.whatsapp","com.instagram.android","com.discord"))assertTrue(pkg,ChatApps.supported(pkg))
        assertFalse(ChatApps.supported("com.android.settings"))
    }
    @Test fun unifiedRetrievalCanUseSeveralRelevantLibrariesWithinABoundedBudget() {
        fun doc(id:String,tags:List<String>)=KnowledgeDocument(id,id,id,tags,"original","## 场景\n幽默接话，先回应具体内容；欣赏与互惠同样重要。")
        val docs=listOf(doc("jev-copilot.md",listOf("expression")),doc("romance-master.md",listOf("expression")),doc("goutou-warmth",listOf("expression","comfort")),doc("goutou-humor",listOf("humor")),doc("goutou-reciprocity",listOf("reciprocity")))
        val result=KnowledgeRouter().unified(KnowledgeQuery("她很累，想幽默接话，有来有回"),docs)
        assertTrue(result.map { it.id }.containsAll(listOf("goutou-warmth","goutou-humor","goutou-reciprocity")))
        assertTrue(result.sumOf { it.text.length }<=9000)
    }
    @Test fun ordinarySelfStatementsDoNotNeedARepeatedFirstPersonPronoun() {
        for((kind,text) in listOf(PersonalKind.DISLIKE to "不吃香菜",PersonalKind.LIKE to "最爱冰美式",PersonalKind.HABIT to "每周六都会去跑步",PersonalKind.LIKE to "I love coffee",PersonalKind.DISLIKE to "I don't like crowded places"))assertTrue(text,LocalPersonalLearning.permitted(kind,text))
        for(text in listOf("你不吃香菜","她最爱冰美式","要不每周六跑步？","以前最爱咖啡","if I love coffee"))assertFalse(text,LocalPersonalLearning.permitted(PersonalKind.LIKE,text))
    }
}
