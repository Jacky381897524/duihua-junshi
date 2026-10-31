package com.loveadvisor.core.capture

/** Reject nearly blank screenshots; a uniform chat background cannot identify a conversation. */
object ConversationFingerprint {
    fun hasDetail(pixels:IntArray):Boolean {
        if(pixels.size<100)return false
        val histogram=IntArray(16)
        for(pixel in pixels) {
            val r=(pixel ushr 16) and 255;val g=(pixel ushr 8) and 255;val b=pixel and 255
            histogram[((r*3+g*6+b)/10) ushr 4]++
        }
        return pixels.size-histogram.maxOrNull()!! >= maxOf(32,pixels.size/50)
    }
}
