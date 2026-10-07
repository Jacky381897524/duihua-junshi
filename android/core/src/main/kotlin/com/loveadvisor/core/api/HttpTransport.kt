package com.loveadvisor.core.api
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
class Cancellation {
    @Volatile var cancelled = false; private set
    private val connections = mutableListOf<HttpURLConnection>()
    @Synchronized fun bind(connection: HttpURLConnection) { if (cancelled) { connection.disconnect(); throw CancellationException() }; connections.add(connection) }
    @Synchronized fun release(connection: HttpURLConnection) { connections.remove(connection) }
    @Synchronized fun cancel() { cancelled = true; connections.forEach { it.disconnect() }; connections.clear() }
    fun check() { if (cancelled) throw CancellationException("请求已取消") }
}
interface HttpTransport { fun execute(request: ApiRequest, cancel: Cancellation): ApiResponse }
