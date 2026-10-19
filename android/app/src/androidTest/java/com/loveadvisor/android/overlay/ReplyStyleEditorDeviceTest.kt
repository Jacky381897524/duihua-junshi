package com.loveadvisor.android.overlay

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.core.advisor.*
import org.junit.Assert.*
import org.junit.Test

class ReplyStyleEditorDeviceTest {
    @Test fun correctedSpecialNicknameAndRecognitionAliasSaveTogetherInThePanel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var saved:ReplyStyleCatalog?=null;var closed=false
            val editor=ReplyStyleEditorView(InstrumentationRegistry.getInstrumentation().targetContext,"com.tencent.mm","My Near",ReplyStyleCatalog(),"全对象",{ saved=it },{ fail("global must stay unchanged") },{ closed=true })
            editor.nicknameInput.setText("𝑴𝒚 𝑵𝒆𝒂𝒓🔥👩🏽‍💻")
            editor.styleInput.setText("温柔简短")
            editor.saveButton.performClick()
            assertTrue(closed)
            assertEquals("温柔简短",saved?.match("My Near","com.tencent.mm")?.style)
            assertEquals("𝑴𝒚 𝑵𝒆𝒂𝒓🔥👩🏽‍💻",saved?.entries()?.single()?.title)
            assertNull(saved?.match("My Near","com.tencent.mobileqq"))
        }
    }
    @Test fun globalEditorSavesWithoutAContactNameOrChangingDedicatedStyles() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var global:String?=null;var closed=false
            val editor=ReplyStyleEditorView(InstrumentationRegistry.getInstrumentation().targetContext,"com.tencent.mm","",ReplyStyleCatalog(),"旧全对象",{ fail("dedicated settings must stay unchanged") },{ global=it },{ closed=true })
            editor.scopeGroup.check(editor.global.id)
            editor.styleInput.setText("全对象不加标点")
            editor.saveButton.performClick()
            assertEquals("全对象不加标点",global);assertTrue(closed)
            assertEquals(View.GONE,editor.nicknameInput.visibility)
        }
    }
    @Test fun cancelClosesWithoutSavingEitherScope() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var closed=false
            val editor=ReplyStyleEditorView(InstrumentationRegistry.getInstrumentation().targetContext,"com.tencent.mm","🔥",ReplyStyleCatalog(),"",{ fail("cancel must not save") },{ fail("cancel must not save") },{ closed=true })
            val back=(0 until editor.childCount).map { editor.getChildAt(it) }.filterIsInstance<android.widget.Button>().last()
            editor.nicknameInput.setText("꧁༺星✨༻꧂");editor.styleInput.setText("尚未保存")
            back.performClick();assertTrue(closed)
        }
    }
}
