package com.loveadvisor.core.capture

import org.junit.Assert.*
import org.junit.Test

class ConversationFingerprintTest {
    @Test fun blankOrNearlyBlankChatCannotIdentifyAContact() {
        val pixels=IntArray(96*24) { 0xff202020.toInt() }
        assertFalse(ConversationFingerprint.hasDetail(pixels))
        repeat(20) { pixels[it]=0xffffffff.toInt() };assertFalse(ConversationFingerprint.hasDetail(pixels))
        repeat(100) { pixels[it]=0xffffffff.toInt() };assertTrue(ConversationFingerprint.hasDetail(pixels))
    }
}
