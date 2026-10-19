package com.loveadvisor.core.session
import com.loveadvisor.core.model.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
class LegacyMessageTest {
    @Test fun oldSavedMessageWithoutKindCanStillEnterTracker() {
        val m=Gson().fromJson("""{"id":"a","speaker":"OTHER","text":"旧对话","sender":null}""",ChatMessage::class.java)
        val snapshot=ChatSnapshot("wechat",1,ConversationIdentity("c","她",ChatKind.DIRECT,true),listOf(m))
        assertNotNull(SessionTracker().update(snapshot))
    }
}
