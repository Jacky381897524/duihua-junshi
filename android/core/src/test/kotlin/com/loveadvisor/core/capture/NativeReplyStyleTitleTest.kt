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
    @Test fun wechatHeaderCanBeExactEvenWhenBodyNodesAreMissing() {
        val name="林佳琪👩🏽‍💻"
        val window=chat.copy(packageName=ChatApps.WECHAT,nodes=listOf(title.copy(id="header",text=name,bounds=RectI(400,115,650,180)),input))
        assertEquals(name,NativeReplyStyleTitle.read(window))
        assertTrue(AutomaticChatParser().extract(window) is CaptureResult.NeedsImage)
        assertNull(NativeReplyStyleTitle.read(window.copy(nodes=window.nodes.dropLast(1))))
    }
    @Test fun standaloneWechatHeaderNeverComesFromProfileOrMultipleHeaderLabels() {
        val window=chat.copy(packageName=ChatApps.WECHAT,windowClassName="com.tencent.mm.plugin.profile.ui.ContactInfoUI",nodes=listOf(title.copy(text="林佳琪"),input))
        assertNull(NativeReplyStyleTitle.read(window))
    }
    @Test fun firstVisibleBodyDoesNotReplaceAnUnexposedWechatHeader() {
        val window=WindowSnapshot(ChatApps.WECHAT,42,RectI(0,0,1000,2000),listOf(
            title.copy(id=null,text="导航下的第一条消息",bounds=RectI(130,200,600,280)),
            input.copy(bounds=RectI(120,1700,800,1800))))
        assertNull(NativeReplyStyleTitle.read(window))
    }
    @Test fun differingHeaderCandidatesRequireImageRatherThanGuessingAName() {
        val header=title.copy(id=null,text="甲",bounds=RectI(400,115,650,180))
        val window=chat.copy(packageName=ChatApps.WECHAT,nodes=listOf(header,header.copy(text="乙",bounds=RectI(400,184,650,220)),input))
        assertNull(NativeReplyStyleTitle.read(window))
        assertEquals("甲",NativeReplyStyleTitle.read(window.copy(nodes=listOf(header,header,input))))
    }
}
