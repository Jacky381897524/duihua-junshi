package com.loveadvisor.core.capture

import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class NativeReplyStyleTitleTest {
    private val title=NodeRecord("com.tencent.mobileqq:id/3kc","🍃    L 。","android.widget.TextView",false,RectI(172,121,350,178))
    private val input=NodeRecord("input","","android.widget.EditText",true,RectI(130,2150,850,2250))
    private val chat=WindowSnapshot("com.tencent.mobileqq",1,RectI(0,0,1080,2344),listOf(title,input))
    @Test fun knownQqChatHeaderRetainsTheExactUnicodeNickname() { assertEquals("🍃    L 。",NativeReplyStyleTitle.read(chat)) }
    @Test fun profilesListsAndOtherPlatformsNeverSupplyAStyleNicknameFromTheSameText() {
        assertNull(NativeReplyStyleTitle.read(chat.copy(nodes=listOf(title))))
        assertNull(NativeReplyStyleTitle.read(chat.copy(packageName="com.ss.android.ugc.aweme")))
        assertNull(NativeReplyStyleTitle.read(chat.copy(nodes=listOf(title.copy(id="list-row",bounds=RectI(172,800,350,850)),input))))
    }
}
