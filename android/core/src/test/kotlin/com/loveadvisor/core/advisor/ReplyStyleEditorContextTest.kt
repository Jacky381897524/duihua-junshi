package com.loveadvisor.core.advisor

import com.loveadvisor.core.capture.ChatApps
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ReplyStyleEditorContextTest {
    @Test fun anotherCurrentNicknameCannotInheritTheCachedDirectOrGroupCatalog() {
        for(kind in listOf(ChatKind.DIRECT,ChatKind.GROUP)) {
            val cached=AdvisorFixtures.snapshot().copy(identity=ConversationIdentity("cached","之前对象",kind,true),replyStyleTitle="旧备注")
            val context=ReplyStyleEditorContext.resolve(cached,ChatApps.WECHAT,"当前对象👩🏽‍💻")
            assertNull(context.kind)
            assertEquals("当前对象👩🏽‍💻",context.title)
        }
    }
    @Test fun matchingFreshPlatformAndNicknameCanReuseTheCachedKindAndCorrectedStyleTitle() {
        val cached=AdvisorFixtures.snapshot().copy(identity=ConversationIdentity("group","群聊(12)",ChatKind.GROUP,true),replyStyleTitle="群聊")
        val matched=ReplyStyleEditorContext.resolve(cached,ChatApps.WECHAT,"群聊(12)")
        assertEquals(ChatKind.GROUP,matched.kind);assertEquals("群聊",matched.title)
        assertNull(ReplyStyleEditorContext.resolve(cached,ChatApps.QQ,"群聊(12)").kind)
        assertNull(ReplyStyleEditorContext.resolve(cached,ChatApps.WECHAT,null).kind)
        assertNull(ReplyStyleEditorContext.resolve(null,ChatApps.WECHAT,"群聊(12)").kind)
    }
}
