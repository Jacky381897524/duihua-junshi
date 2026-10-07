package com.loveadvisor.core.api
import org.junit.Assert.*
import org.junit.Test
class EndpointResolverTest {
    private val resolver = EndpointResolver()
    @Test fun preservesCustomPrefixAndRemovesCompleteChatRoute() {
        val urls = mapOf("https://example.test/v1/" to "https://example.test/v1", "https://example.test/openai/v1/chat/completions" to "https://example.test/openai/v1", "https://example.test/api/model" to "https://example.test/api/model")
        urls.forEach { (input, base) ->
            val e=resolver.resolve(ApiProfile(baseUrl=input,apiKey="test"))
            assertEquals(listOf("$base/models"),e.modelsCandidates)
            assertEquals("$base/chat/completions",e.chatCompletions)
        }
    }
    @Test fun rootHasSameOrigin404FallbackOnly() {
        assertEquals(listOf("https://example.test/models","https://example.test/v1/models"),resolver.resolve(ApiProfile(baseUrl="https://example.test",apiKey="test")).modelsCandidates)
    }
    @Test fun unsafeUrlsAreRejected() {
        for(url in listOf("ftp://example.test","https://key@example.test/v1","https://example.test/v1?key=bad","https://example.test/v1#secret","http://public.example.test")) {
            try { resolver.resolve(ApiProfile(baseUrl=url,apiKey="secret")); fail("URL should be rejected") } catch(e: IllegalArgumentException) { assertFalse(e.message.orEmpty().contains("secret")) }
        }
    }
    @Test fun localHttpRequiresExplicitFlag() {
        try { resolver.resolve(ApiProfile(baseUrl="http://127.0.0.1:8123",apiKey="test")); fail() } catch(_:IllegalArgumentException) { }
        assertEquals("http://127.0.0.1:8123/chat/completions",resolver.resolve(ApiProfile(baseUrl="http://127.0.0.1:8123",apiKey="test",allowLanHttp=true)).chatCompletions)
    }
}
