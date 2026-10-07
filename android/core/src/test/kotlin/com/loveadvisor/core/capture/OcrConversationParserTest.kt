package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test
class OcrConversationParserTest {
    @Test fun leftAlignedLarkOcrNeverAssumesEveryMessageIsOther() {
        val d=OcrConversationParser().parse(listOf(OcrTextBox("第一句",RectI(130,400,500,500)),OcrTextBox("第二句",RectI(130,600,500,700))),CaptureGeometry(RectI(0,0,1000,2000),packageName="com.ss.android.lark"))
        assertEquals(listOf(Speaker.UNKNOWN,Speaker.UNKNOWN),d.messages.map { it.speaker })
    }
    private val g=CaptureGeometry(RectI(0,0,1000,2000),1700)
    @Test fun longBubblesDoNotAllBecomeOtherAndUiTextIsExcluded() {
        val d=OcrConversationParser().parse(listOf(OcrTextBox("小雨",RectI(400,90,600,150)),OcrTextBox("18:59",RectI(430,300,570,350)),OcrTextBox("左边很长的一句话",RectI(140,500,790,650)),OcrTextBox("右边很长的一句话",RectI(210,700,860,850)),OcrTextBox("输入中的草稿",RectI(150,1730,760,1800))),g)
        assertEquals(listOf(Speaker.OTHER,Speaker.ME),d.messages.map { it.speaker }); assertEquals("小雨",d.title)
    }
    @Test fun centeredWideTextRequiresDirectionConfirmationAndGroupSenderIsKept() {
        val d=OcrConversationParser().parse(listOf(OcrTextBox("小组(3)",RectI(300,90,700,150)),OcrTextBox("你好",RectI(140,400,860,600),sender="成员甲")),g)
        assertFalse(d.speakerMappingConfirmed); assertEquals(Speaker.UNKNOWN,d.messages.single().speaker); assertEquals("成员甲",d.messages.single().sender)
    }
}
