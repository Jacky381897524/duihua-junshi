package com.loveadvisor.core.advisor

import org.junit.Assert.*
import org.junit.Test

class RealisticWarmthTest {
    @Test fun pleasingIsAllowedByContextWithoutRemovingExplicitBoundaries() {
        val rules=RealityReplyPolicy.instruction
        assertTrue(rules.contains("可以真诚讨好"));assertTrue(rules.contains("情绪价值"));assertTrue(rules.contains("明确拒绝"))
        val real=ResponsePolicy.dataFor(true,true).toString()
        assertTrue(real.contains("可以真诚讨好"));assertTrue(real.contains("不因热情扣分"))
        assertFalse(ResponsePolicy.dataFor(true,false)["objective"].toString().contains("可以真诚讨好"))
        assertEquals(ExpressionMode.REALISTIC,RealityReplyPolicy.mode(true,true))
        assertEquals(ExpressionMode.LEGACY,RealityReplyPolicy.mode(false,false))
    }
}
