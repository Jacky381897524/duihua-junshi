package com.loveadvisor.core.capture

import com.loveadvisor.core.model.RectI
import org.junit.Assert.*
import org.junit.Test

class ReplyStyleHeaderTest {
    @Test fun repeatedCacheRowsDoNotMakeTheSameSpellingAnOcrMiss() {
        val boxes=listOf(OcrTextBox("23:34",RectI(160,5,230,28)),OcrTextBox("5G",RectI(350,5,390,28)),OcrTextBox("小林",RectI(160,65,230,100)))
        assertEquals("小林",ReplyStyleHeader.cachedTitle(boxes,List(7) { "小林" }+"小雨",500,160))
        assertEquals("小林",ReplyStyleHeader.toolbarTitle(boxes,500,160))
        assertEquals(RectI(0,57,500,108),ReplyStyleHeader.fingerprintBand(boxes,500,160))
        val cropped=boxes.takeLast(1).map { it.copy(bounds=RectI(160,5,230,40)) }
        assertEquals("小林",ReplyStyleHeader.toolbarTitle(cropped,500,160))
        assertEquals(0,ReplyStyleHeader.fingerprintBand(cropped,500,160)!!.top)
    }
    @Test fun captureCropIncludesToolbarWhetherSystemBarPixelsArePresentOrAbsent() {
        assertEquals(RectI(129,0,972,249),ReplyStyleHeader.captureCrop(ChatApps.WECHAT,RectI(0,0,1080,2344),95,2.75f))
    }
    @Test fun keyboardResizeUsesUniformToolbarPixelsEvenBeforeScreenshotDimensionsSettle() {
        for(height in listOf(2344,1400,1100,1800,2344)) {
            val v=RectI(0,0,1080,height)
            val crop=ReplyStyleHeader.captureCrop(ChatApps.WECHAT,v,95,2.75f)!!
            assertEquals(RectI(129,0,972,249),ReplyStyleHeader.bitmapCrop(crop,v,1080,2344))
            assertEquals(RectI(64,0,486,124),ReplyStyleHeader.bitmapCrop(crop,v,540,1172))
        }
        val split=RectI(80,300,1160,1700)
        assertEquals(RectI(129,0,972,154),ReplyStyleHeader.bitmapCrop(ReplyStyleHeader.captureCrop(ChatApps.WECHAT,split,95,2.75f)!!,split,1080,2344))
        assertNull(ReplyStyleHeader.bitmapCrop(RectI(0,0,50,50),split,0,2344))
    }
    @Test fun clippedLongRemarksCannotBeMistakenForAnotherShortCachedTitle() {
        for(rect in listOf(RectI(0,25,260,60),RectI(160,25,500,60))) {
            val boxes=listOf(OcrTextBox("熟悉的昵称",rect))
            assertNull(ReplyStyleHeader.toolbarTitle(boxes,500,100))
            assertNull(ReplyStyleHeader.cachedTitle(boxes,listOf("熟悉的昵称"),500,100))
        }
    }
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
