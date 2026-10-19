package com.loveadvisor.core.capture

import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class ReplyStyleHeaderTest {
    @Test fun cropStaysInToolbarAndExcludesLeftBackAndAvatarArea() {
        assertEquals(RectI(270,95,907,249),ReplyStyleHeader.crop(ChatApps.WECHAT,RectI(0,0,1080,2344),95,2.75f))
        assertEquals(RectI(120,100,840,212),ReplyStyleHeader.crop(ChatApps.QQ,RectI(0,100,1000,1600),80,2f))
        assertNull(ReplyStyleHeader.crop("unrelated.app",RectI(0,0,1080,2344),95,2.75f))
    }
    @Test fun titlePreservesUnicodeAndNeverGuessesBetweenMultipleTextBlocks() {
        val title="꧁༺𝑵𝒆𝒂𝒓👩🏽‍💻༻꧂"
        assertEquals(title,ReplyStyleHeader.title(listOf(OcrTextBox(title,RectI(0,5,300,50)))))
        assertNull(ReplyStyleHeader.title(listOf(OcrTextBox("JoJo桑",RectI(0,5,150,50)),OcrTextBox("325",RectI(160,5,220,50)))))
        assertNull(ReplyStyleHeader.title(listOf(OcrTextBox("消息",RectI(0,5,200,50)))))
        assertNull(ReplyStyleHeader.title(listOf(OcrTextBox("姓名\n其他文字",RectI(0,0,200,100)))))
    }
}
