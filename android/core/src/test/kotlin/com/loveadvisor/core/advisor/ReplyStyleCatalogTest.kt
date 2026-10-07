package com.loveadvisor.core.advisor
import org.junit.Assert.*
import org.junit.Test

class ReplyStyleCatalogTest {
    @Test fun sameNameOnDifferentAppsKeepsIndependentStylesAndDeletion() {
        var c=ReplyStyleCatalog().save("小雨","温柔")
        c=c.save("小雨","简短",packageName="com.tencent.mobileqq")
        assertEquals(2,c.entries().size)
        assertEquals(setOf("com.tencent.mm","com.tencent.mobileqq"),c.entries().map { it.packageName }.toSet())
        c=c.remove(c.entries().single { it.packageName=="com.tencent.mobileqq" }.key)
        assertEquals("温柔",c.entries().single().style)
        assertEquals("com.tencent.mm",c.apps.values.single())
    }
    @Test fun savedObjectsCanBeListedReadAndIndependentlyDeleted() {
        var c=ReplyStyleCatalog()
        c=c.save("小雨","无标点")
        c=c.save("小林","简短")
        assertEquals(setOf("小雨","小林"),c.entries().map { it.title }.toSet())
        assertEquals("无标点",c.entries().single { it.title=="小雨" }.style)
        c=c.remove(c.entries().single { it.title=="小雨" }.key)
        assertEquals(listOf("小林"),c.entries().map { it.title })
        assertEquals("简短",c.styles.values.single())
    }
    @Test fun unnamedLegacyStylesArePreservedAndCanBeNamedWithoutDuplicates() {
        val key=ReplyStyles.key("com.tencent.mm","小雨")!!
        var c=ReplyStyleCatalog(mapOf(key to "温柔"))
        assertNull(c.entries().single().title)
        c=c.rememberTitle("小雨")
        assertEquals("小雨",c.entries().single().title)
        c=c.save("小雨","浪漫")
        assertEquals(1,c.entries().size)
        assertEquals("浪漫",c.entries().single().style)
    }
    @Test fun editingLegacyEntryRenamesOnlyThatEntryAndBlankSaveRestoresDefault() {
        var c=ReplyStyleCatalog(mapOf("legacy" to "长一点")).save("小林","简短")
        c=c.save("小雨","温柔",replacingKey="legacy")
        assertFalse(c.styles.containsKey("legacy"))
        c=c.save("小雨","  ")
        assertEquals(listOf("小林"),c.entries().map { it.title })
        assertEquals(1,c.titles.size)
    }
}
