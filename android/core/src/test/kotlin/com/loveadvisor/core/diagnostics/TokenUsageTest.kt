package com.loveadvisor.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class TokenUsageTest {
    @Test fun onlyProviderCountersAreProjectedAndMissingUsageIsUnknown() {
        assertEquals(mapOf(LogMetric.INPUT_TOKENS to 123L,LogMetric.OUTPUT_TOKENS to 45L,LogMetric.CACHED_INPUT_TOKENS to 100L),TokenUsage.counters("""{"choices":[{"message":{"content":"不应进入日志的内容"}}],"usage":{"prompt_tokens":123,"completion_tokens":45,"prompt_tokens_details":{"cached_tokens":100}}}"""))
        assertTrue(TokenUsage.counters("""{"choices":[]}""").isEmpty())
    }
    @Test fun invalidOrUntrustedCounterTypesAreIgnored() {
        assertTrue(TokenUsage.counters("""{"usage":{"prompt_tokens":-1,"completion_tokens":"秘密"}}""").isEmpty())
        assertTrue(TokenUsage.counters("""{"usage":{"prompt_tokens":1.5}}""").isEmpty())
        assertTrue(TokenUsage.counters("broken").isEmpty())
    }
    @Test fun compatibleInputOutputNamesRemainNumeric() {
        assertEquals(mapOf(LogMetric.INPUT_TOKENS to 1000L,LogMetric.OUTPUT_TOKENS to 20L),TokenUsage.counters("""{"usage":{"input_tokens":1000,"output_tokens":20}}"""))
    }
}
