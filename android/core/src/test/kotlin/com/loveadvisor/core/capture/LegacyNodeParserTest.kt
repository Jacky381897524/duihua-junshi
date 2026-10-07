package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class LegacyNodeParserTest {
    private val input=NodeRecord("input","","EditText",true,RectI(100,1700,800,1800))
    @Test fun qqRetainsOldBodySenderIdsAndLongBubbleSide() {
        val body=NodeRecord("com.tencent.mobileqq:id/mjn","你好","TextView",false,RectI(130,500,790,700))
        val sender=NodeRecord("com.tencent.mobileqq:id/mjq","成员甲","TextView",false,RectI(130,450,400,480))
        val r=LegacyNodeParser().extract(WindowSnapshot("com.tencent.mobileqq",1,RectI(0,0,1000,2000),listOf(input,body,sender))) as CaptureResult.NeedsPreview
        assertEquals(Speaker.OTHER,r.draft.messages.single().speaker); assertEquals("成员甲",r.draft.messages.single().sender)
    }
    @Test fun xUsesAccessibleSenderRatherThanFullWidthGeometry() {
        val body=NodeRecord(null,null,"android.view.View",false,RectI(0,500,1000,700),contentDescription="你：今天有点忙。。18:59。Read。")
        val r=LegacyNodeParser().extract(WindowSnapshot("com.twitter.android",1,RectI(0,0,1000,2000),listOf(input,body))) as CaptureResult.NeedsPreview
        assertEquals(Speaker.ME,r.draft.messages.single().speaker); assertEquals("今天有点忙",r.draft.messages.single().text)
    }
    @Test fun feishuReadMarkerDeterminesLeftAlignedOwnBubble() {
        val bubble=NodeRecord("p:id/bubble_content_container",null,"View",false,RectI(100,400,800,700),key="row/bubble")
        val body=NodeRecord(null,"已经完成","TextView",false,RectI(130,450,500,550))
        val read=NodeRecord("p:id/time_read_state_container_align_bubble",null,"View",false,RectI(130,600,500,650),parentKey="row/bubble/receipt")
        val r=LegacyNodeParser().extract(WindowSnapshot("com.ss.android.lark",1,RectI(0,0,1000,2000),listOf(input,bubble,body,read))) as CaptureResult.NeedsPreview
        assertEquals(Speaker.ME,r.draft.messages.single().speaker)
    }
}
