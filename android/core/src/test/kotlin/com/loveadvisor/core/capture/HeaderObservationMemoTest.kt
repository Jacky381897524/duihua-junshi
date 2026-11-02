package com.loveadvisor.core.capture

import com.loveadvisor.core.advisor.CachedHeaderProof
import org.junit.Assert.*
import org.junit.Test

class HeaderObservationMemoTest {
    @Test fun repeatedUncachedObjectObservationsStaySettledUntilTheHeaderChanges() {
        val memo=HeaderObservationMemo();val proof=CachedHeaderProof("a".repeat(64),1080,2344)
        memo.remember("com.tencent.mm",1,proof,"未读对象",null)
        repeat(30) { assertTrue(memo.unchanged("com.tencent.mm",1,proof,"未读对象",null,false)) }
        val next=proof.copy(stamp="b".repeat(64))
        assertTrue(memo.changedHeader("com.tencent.mm",1,next))
        assertFalse(memo.unchanged("com.tencent.mm",1,next,"潘佳琪","cached",false))
        memo.remember("com.tencent.mm",1,next,"潘佳琪","cached")
        assertTrue(memo.unchanged("com.tencent.mm",1,next,"潘佳琪","cached",false))
        assertFalse(memo.unchanged("com.tencent.mm",1,next,"潘佳琪",null,false))
    }
    @Test fun anotherWindowMissingProofAndAmbiguousConversationCannotReuseASettledResult() {
        val memo=HeaderObservationMemo();val proof=CachedHeaderProof("a".repeat(64),1080,2344,"b".repeat(64))
        memo.remember("com.tencent.mm",1,proof,"同名","a")
        assertFalse(memo.unchanged("com.tencent.mm",2,proof,"同名","a",false))
        assertFalse(memo.unchanged("com.tencent.mobileqq",1,proof,"同名","a",false))
        assertFalse(memo.unchanged("com.tencent.mm",1,null,"同名","a",false))
        assertFalse(memo.unchanged("com.tencent.mm",1,proof.copy(conversationStamp="c".repeat(64)),"同名","a",true))
        memo.clear();assertFalse(memo.unchanged("com.tencent.mm",1,proof,"同名","a",false))
    }
}
