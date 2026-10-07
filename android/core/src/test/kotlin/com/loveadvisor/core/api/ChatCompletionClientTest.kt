package com.loveadvisor.core.api
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ChatCompletionClientTest {
    @Test fun malformedKeyIsRejectedBeforeTransportAndCannotLeak() {
        LocalApiServer { ServerReply(200,"{}") }.use { server ->
            try { ChatCompletionClient(JdkHttpTransport()).complete(server.profile.copy(apiKey="SECRET\r\nInjected: value"),emptyList(),Cancellation()); fail() }
            catch(e:Exception) { assertFalse(e.message.orEmpty().contains("SECRET")); assertTrue(e is IllegalArgumentException) }
            assertTrue(server.requests.isEmpty())
        }
    }
    @Test fun sendsRealModelIdAndParsesAssistantText() {
        LocalApiServer { ServerReply(200,"""{"choices":[{"message":{"role":"assistant","content":"今天过得怎么样"}}]}""") }.use { server ->
            val text=ChatCompletionClient(JdkHttpTransport()).complete(server.profile,listOf(PromptMessage("user","你好")),Cancellation())
            assertEquals("今天过得怎么样",text)
            val request=server.requests.single(); assertEquals("POST",request.method); assertEquals("/v1/chat/completions",request.path)
            assertEquals("Bearer FAKE_KEY",request.authorization)
            assertEquals("model-real-id",JsonParser.parseString(request.body).asJsonObject.get("model").asString)
            assertFalse(request.path.contains("FAKE_KEY"))
        }
    }
    @Test fun unselectedModelNeverCallsService() {
        LocalApiServer { ServerReply(200,"{}") }.use { server ->
            try { ChatCompletionClient(JdkHttpTransport()).complete(server.profile.copy(selectedModelId=null),emptyList(),Cancellation()); fail() } catch(_:IllegalArgumentException) { }
            assertTrue(server.requests.isEmpty())
        }
    }
    @Test fun serverErrorCannotLeakEchoedKey() {
        LocalApiServer { ServerReply(403,"FAKE_KEY") }.use { server ->
            try { ChatCompletionClient(JdkHttpTransport()).complete(server.profile,emptyList(),Cancellation()); fail() } catch(e:ApiException) { assertEquals(403,e.status); assertFalse(e.message.orEmpty().contains("FAKE_KEY")) }
        }
    }
    @Test fun noCredentialsForwardedToRedirectTarget() {
        LocalApiServer { ServerReply(200,"{}") }.use { target ->
            LocalApiServer { ServerReply(302,"",mapOf("Location" to target.profile.baseUrl)) }.use { origin ->
                val result=ModelCatalogClient(JdkHttpTransport()).list(origin.profile,Cancellation())
                assertTrue(result.error.orEmpty().contains("302")); assertTrue(target.requests.isEmpty())
            }
        }
    }
}
