package com.loveadvisor.core.api
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CancellationException

class JdkHttpTransport(private val connectTimeout: Int = 15000, private val readTimeout: Int = 90000) : HttpTransport {
    override fun execute(request: ApiRequest, cancel: Cancellation): ApiResponse {
        cancel.check()
        val url=URL(request.url)
        require(url.protocol in listOf("https","http") && url.userInfo==null) { "服务地址无效" }
        if(url.protocol=="http") {
            require(request.allowLanHttp && EndpointResolver.isLocalHost(url.host)) { "尚未启用局域网 HTTP" }
            require(InetAddress.getAllByName(url.host).all { it.isLoopbackAddress || it.isSiteLocalAddress }) { "HTTP 服务必须位于本地网络" }
        }
        val connection=url.openConnection() as HttpURLConnection
        cancel.bind(connection)
        try {
            connection.instanceFollowRedirects=false
            connection.connectTimeout=connectTimeout; connection.readTimeout=readTimeout
            connection.requestMethod=request.method
            connection.setRequestProperty("Accept","application/json")
            request.headers.forEach { (k,v) -> connection.setRequestProperty(k,v) }
            request.body?.let {
                connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                connection.outputStream.use { stream -> cancel.check(); stream.write(it.toByteArray(Charsets.UTF_8)) }
            }
            cancel.check()
            val status=connection.responseCode
            if(status in 300..399) throw ApiException(status,"HTTP $status：请填写最终服务地址，未向重定向目标发送密钥")
            val stream=if(status in 200..299) connection.inputStream else connection.errorStream
            val output=ByteArrayOutputStream()
            stream?.use {
                val buffer=ByteArray(8192)
                while(true) {
                    cancel.check(); val n=it.read(buffer); if(n<0) break
                    if(output.size()+n>2*1024*1024) throw ApiException(status,"服务响应超过 2MiB 限制")
                    output.write(buffer,0,n)
                }
            }
            return ApiResponse(status,output.toString("UTF-8"),connection.headerFields.filterKeys { it!=null }.mapValues { it.value.joinToString(",") })
        } catch(e:CancellationException) { throw e }
        catch(e:ApiException) { throw e }
        catch(_:SocketTimeoutException) { cancel.check(); throw ApiException(message="网络请求超时，请稍后重试") }
        catch(_:java.io.IOException) { cancel.check(); throw ApiException(message="网络连接失败，请检查服务地址和网络") }
        finally { cancel.release(connection); connection.disconnect() }
    }
}
