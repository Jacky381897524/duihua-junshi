package com.loveadvisor.core.api
import org.junit.Assert.*
import org.junit.Test
class ProviderKeyTest {
    @Test fun automaticReuseNeverSendsAnotherServicesKeyToOpenRouter() {
        val custom=ApiProfile(baseUrl="https://example.com/v1",apiKey="custom-secret")
        assertEquals("",ProviderPresets.openRouterKey(custom,ProviderPresets.OPENROUTER.vision()))
        assertEquals("",ProviderPresets.openRouterKey(ProviderPresets.DEEPSEEK.reply("deepseek-secret"),ProviderPresets.QWEN.vision("qwen-secret")))
        assertEquals("or-key",ProviderPresets.openRouterKey(ProviderPresets.OPENROUTER.reply("or-key"),custom))
        assertEquals(ProviderPresets.CUSTOM,ProviderPresets.detect(custom))
    }
}
