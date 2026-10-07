package com.loveadvisor.core.api
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.*
import org.junit.Test
class ModelCatalogClientTest {
    @Test fun transientGetRetriesOnceButPersistentFailureIsBounded() {
        var count=0
        server({ count++; if(count==1) 503 to "{}" else 200 to """{"data":[{"id":"actual"}]}""" }) { p,paths ->
            assertEquals("actual",ModelCatalogClient(JdkHttpTransport()).list(p,Cancellation()).models.singleOrNull()?.id); assertEquals(2,paths.size)
        }
        server({503 to "{}"}) { p,paths ->
            assertNotNull(ModelCatalogClient(JdkHttpTransport()).list(p,Cancellation()).error); assertEquals(2,paths.size)
        }
    }
    private fun server(handler: (String)->Pair<Int,String>, test: (ApiProfile,MutableList<String>)->Unit) {
        val paths=mutableListOf<String>(); val s=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        s.createContext("/") { ex ->
            synchronized(paths) { paths.add(ex.requestURI.path) }
            val (code,body)=handler(ex.requestURI.path); val bytes=body.toByteArray()
            ex.sendResponseHeaders(code,bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }; ex.close()
        }
        s.start()
        try { test(ApiProfile(baseUrl="http://127.0.0.1:${s.address.port}",apiKey="FAKE_KEY",allowLanHttp=true,revision=7),paths) } finally { s.stop(0) }
    }
    @Test fun root404FallbackParsesDedupesAndPreservesId() = server({ p -> if(p=="/models") 404 to "{}" else 200 to """{"data":[{"id":"z","name":"展示名"},{"id":"a"},{"id":"z"}]}""" }) { profile,paths ->
        val result=ModelCatalogClient(JdkHttpTransport()).list(profile,Cancellation())
        assertNull(result.error); assertEquals(listOf("a","z"),result.models.map { it.id }); assertEquals("展示名",result.models[1].name)
        assertEquals(listOf("/models","/v1/models"),paths); assertEquals(7,result.profileRevision); assertTrue(result.resolvedBaseUrl!!.endsWith("/v1"))
    }
    @Test fun unauthorizedDoesNotProbeFallback() = server({401 to "do not expose FAKE_KEY"}) { profile,paths ->
        val r=ModelCatalogClient(JdkHttpTransport()).list(profile,Cancellation())
        assertTrue(r.error.orEmpty().contains("401")); assertFalse(r.error.orEmpty().contains("FAKE_KEY")); assertEquals(listOf("/models"),paths)
    }
    @Test fun emptyAndNonJsonAreExplicitFailures() {
        for(body in listOf("<html>wrong route</html>","{\"data\":[]}")) server({200 to body}) { p,_ ->
            val r=ModelCatalogClient(JdkHttpTransport()).list(p,Cancellation()); assertNotNull(r.error); assertTrue(r.models.isEmpty())
        }
    }
    @Test fun timeoutIsReported() = server({ Thread.sleep(150); 200 to "{}" }) { p,_ ->
        val r=ModelCatalogClient(JdkHttpTransport(readTimeout=30)).list(p,Cancellation()); assertNotNull(r.error)
    }
    @Test fun cancellationStopsRequest() {
        val c=Cancellation(); c.cancel()
        try { JdkHttpTransport().execute(ApiRequest("GET","https://example.test"),c); fail() } catch(_:java.util.concurrent.CancellationException) { }
    }
}
