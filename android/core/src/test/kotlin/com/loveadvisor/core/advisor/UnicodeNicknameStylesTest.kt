package com.loveadvisor.core.advisor

import com.loveadvisor.core.model.*
import org.junit.Assert.*
import org.junit.Test

class UnicodeNicknameStylesTest {
    private fun snapshot(title:String,app:String="com.tencent.mm")=AdvisorFixtures.snapshot().copy(packageName=app,identity=ConversationIdentity("contact",title,ChatKind.DIRECT,true))

    @Test fun canonicallyEquivalentAccentsUseTheSameDedicatedStyle() {
        val styles=ReplyStyles.save(emptyMap(),"com.tencent.mm","Cafe\u0301","温柔")
        assertEquals("温柔",ReplyStyles.forSnapshot(styles,snapshot("Café"),"全对象"))
    }
    @Test fun editingAnEquivalentUnicodeNicknameDoesNotCreateDuplicateContacts() {
        val catalog=ReplyStyleCatalog().save("Cafe\u0301","旧风格").save("Café","新风格")
        assertEquals(1,catalog.entries().size)
        assertEquals("新风格",catalog.entries().single().style)
    }
    @Test fun emojiSequencesAndForeignScriptsRemainDistinctAndUnmodified() {
        val names=listOf("🔥","🍃    L 。","คิดถึง ๖","꧁༺星✨༻꧂","👩🏽‍💻","👩‍👩‍👧‍👦","👩👩👧👦","❤️","❤","𝑴𝒚 𝑭𝒂𝒓𝒂𝒘𝒂𝒚 𝑵𝒆𝒂𝒓")
        var catalog=ReplyStyleCatalog()
        names.forEachIndexed { index,name -> catalog=catalog.save(name,"风格$index") }
        assertEquals(names.toSet(),catalog.entries().map { it.title }.toSet())
        names.forEachIndexed { index,name -> assertEquals("风格$index",ReplyStyles.forSnapshot(catalog.styles,snapshot(name),"全对象")) }
    }
    @Test fun visuallySimilarLettersAndDifferentPlatformsDoNotSilentlyShareStyles() {
        val styles=ReplyStyles.save(emptyMap(),"com.tencent.mm","A","专属")
        assertEquals("全对象",ReplyStyles.forSnapshot(styles,snapshot("А"),"全对象")) // Cyrillic A
        assertEquals("全对象",ReplyStyles.forSnapshot(styles,snapshot("A","com.tencent.mobileqq"),"全对象"))
    }
    @Test fun confirmedRecognitionAliasFollowsEditsAndIsRemovedWithItsStyle() {
        var catalog=ReplyStyleCatalog().save("𝑴𝒚 𝑵𝒆𝒂𝒓","浪漫")
        val key=catalog.entries().single().key
        assertNull(catalog.match("My Near","com.tencent.mm"))
        assertEquals(key,catalog.suggestions("My Near","com.tencent.mm").single().key)
        catalog=catalog.bindAlias("My Near",key,"com.tencent.mm")
        assertEquals("浪漫",catalog.match("My Near","com.tencent.mm")?.style)
        assertNull(catalog.match("My Near","com.tencent.mobileqq"))
        catalog=catalog.save("𝑴𝒚 𝑵𝒆𝒂𝒓","简短")
        assertEquals("简短",catalog.match("My Near","com.tencent.mm")?.style)
        assertNull(catalog.remove(key).match("My Near","com.tencent.mm"))
    }
    @Test fun compatibilitySuggestionsNeverAutomaticallyMergeDifferentNicknameStyles() {
        val catalog=ReplyStyleCatalog().save("Ａ  Ｂ","甲").save("A B","乙")
        assertNull(catalog.match("A   B","com.tencent.mm"))
        assertEquals(2,catalog.suggestions("A   B","com.tencent.mm").size)
        assertEquals("乙",catalog.match("A B","com.tencent.mm")?.style)
    }
    @Test fun aliasCannotOverwriteAnotherContactsExactName() {
        val catalog=ReplyStyleCatalog().save("原昵称","甲").save("另一个人","乙")
        val key=catalog.entries().single { it.title=="原昵称" }.key
        try { catalog.bindAlias("另一个人",key,"com.tencent.mm");fail("conflicting nickname must not be reassigned") }
        catch(_:IllegalArgumentException) { }
        assertEquals("乙",catalog.match("另一个人","com.tencent.mm")?.style)
    }
    @Test fun renamingAnEntryKeepsConfirmedRecognitionAliasesOnTheSamePlatform() {
        var catalog=ReplyStyleCatalog().save("旧备注","温柔")
        val old=catalog.entries().single().key
        catalog=catalog.bindAlias("OCR名字",old,"com.tencent.mm").save("新备注","简短",old)
        assertEquals("简短",catalog.match("OCR名字","com.tencent.mm")?.style)
        assertEquals("新备注",catalog.match("OCR名字","com.tencent.mm")?.title)
        assertNull(catalog.match("旧备注","com.tencent.mm"))
    }
    @Test fun rawNicknameForStyleDoesNotRewriteTheConversationIdentity() {
        val original=snapshot("L")
        val withNativeName=original.copy(replyStyleTitle="🍃    L 。")
        val styles=ReplyStyles.save(emptyMap(),"com.tencent.mm","🍃    L 。","简短")
        assertEquals("简短",ReplyStyles.forSnapshot(styles,withNativeName,"全对象"))
        assertEquals("L",withNativeName.identity?.displayTitle)
        assertEquals(original.identity,withNativeName.identity)
    }
}
