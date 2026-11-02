package com.loveadvisor.core.capture

import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class ReplyStyleHeaderTest {
    @Test fun separatedLatinAndChineseTitlePartsCanIdentifyTheFullCachedName() {
        val parts=listOf(OcrTextBox("Ny",RectI(140,25,180,60)),OcrTextBox("赵嘉豪",RectI(185,25,285,60)))
        assertEquals("Ny 赵嘉豪",ReplyStyleHeader.toolbarTitle(parts,500,100))
        assertEquals("Ny赵嘉豪",ReplyStyleHeader.cachedTitle(parts,listOf("Ny赵嘉豪","Ny赵崇康"),500,100))
        assertNull(ReplyStyleHeader.cachedTitle(parts,listOf("Ny赵崇康"),500,100))
    }
    @Test fun cachedTitlesCanBeFoundAmongMultipleToolbarLinesWithoutGuessing() {
        val name=OcrTextBox("My Faraway Near",RectI(160,35,260,65))
        val other=OcrTextBox("22",RectI(0,30,30,60))
        val hints=listOf("My Faraway Near","潘佳琪")
        assertEquals("My Faraway Near",ReplyStyleHeader.cachedTitle(listOf(name,other),hints,500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(name),listOf("My Faraway Near","MyFarawayNear"),500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(name.copy(text="My Faraway Neat")),hints,500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(name.copy(text="my faraway near")),hints,500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(name.copy(bounds=RectI(450,35,495,65))),hints,500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(name,OcrTextBox("潘佳琪",RectI(170,40,230,65))),hints,500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(name.copy(text="消息")),listOf("消息"),500,100))
    }
    @Test fun whitespaceDifferencesInMixedChineseNamesAreMatchedOnlyWhenUnique() {
        val recognized=OcrTextBox("Ny 赵嘉豪",RectI(160,35,260,65))
        assertEquals("Ny赵嘉豪",ReplyStyleHeader.cachedTitle(listOf(recognized),listOf("Ny赵嘉豪","Ny赵崇康"),500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(recognized),listOf("Ny赵嘉豪","Ny 赵嘉豪"),500,100))
        assertNull(ReplyStyleHeader.cachedTitle(listOf(recognized),listOf("Ny赵崇康"),500,100))
    }
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
