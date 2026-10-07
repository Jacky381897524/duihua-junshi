package com.loveadvisor.core.api
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
data class RecordedRequest(val method:String,val path:String,val authorization:String?,val body:String)
data class ServerReply(val status:Int,val body:String,val headers:Map<String,String> = emptyMap())
class LocalApiServer(private val handler:(RecordedRequest)->ServerReply):AutoCloseable {
    val requests=CopyOnWriteArrayList<RecordedRequest>()
    private val executor=Executors.newCachedThreadPool { Thread(it).apply { isDaemon=true } }
    private val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0).apply {
        this.executor=this@LocalApiServer.executor
        createContext("/") { ex ->
            try {
                val request=RecordedRequest(ex.requestMethod,ex.requestURI.path,ex.requestHeaders.getFirst("Authorization"),ex.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() })
                requests.add(request)
                val reply=handler(request); val bytes=reply.body.toByteArray(Charsets.UTF_8)
                reply.headers.forEach { (k,v) -> ex.responseHeaders.add(k,v) }
                ex.sendResponseHeaders(reply.status,bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }
            } catch(_:java.io.IOException) { } finally { ex.close() }
        }
        start()
    }
    val profile get()=ApiProfile(baseUrl="http://127.0.0.1:${server.address.port}/v1",apiKey="FAKE_KEY",selectedModelId="model-real-id",allowLanHttp=true)
    override fun close() { server.stop(0); executor.shutdownNow() }
}
