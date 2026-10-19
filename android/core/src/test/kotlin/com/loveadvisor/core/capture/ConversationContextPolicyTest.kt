package com.loveadvisor.core.capture
import com.loveadvisor.core.model.ChatKind
import org.junit.Assert.*
import org.junit.Test
class ConversationContextPolicyTest {
    @Test fun groupRangeIsExplicitAndPrivateChatsRemainFifty() {
        assertEquals(100,ConversationContextPolicy.limit(ChatKind.GROUP))
        for(n in listOf(50,100,200))assertEquals(n,ConversationContextPolicy.limit(ChatKind.GROUP,n))
        for(n in listOf(-1,0,51,500))assertEquals(100,ConversationContextPolicy.limit(ChatKind.GROUP,n))
        assertEquals(50,ConversationContextPolicy.limit(ChatKind.DIRECT,200))
    }
}
