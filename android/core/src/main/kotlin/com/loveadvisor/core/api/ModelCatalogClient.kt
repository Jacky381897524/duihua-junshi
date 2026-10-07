package com.loveadvisor.core.api
import com.google.gson.JsonParser
import java.util.concurrent.CancellationException

class ModelCatalogClient(private val transport: HttpTransport, private val resolver: EndpointResolver = EndpointResolver()) {
    fun list(profile: ApiProfile, cancel: Cancellation): CatalogResult {
        return try {
            require(profile.apiKey.isNotBlank()) { "请填写 API Key" }
            require(profile.apiKey.none { it.isISOControl() }) { "API Key 包含无效字符，请重新填写" }
            val routes=resolver.resolve(profile).modelsCandidates
            var response:ApiResponse?=null; var selected:String?=null
            for(route in routes) {
                response=getWithRetry(ApiRequest("GET",route,mapOf("Authorization" to "Bearer ${profile.apiKey}"),allowLanHttp=profile.allowLanHttp),cancel)
                if(response.status!=404) { selected=route; break }
            }
            val r=response ?: throw ApiException(message="没有可用的模型目录地址")
            if(r.status !in 200..299) throw ApiException(r.status,httpError(r.status))
            val json=try { JsonParser.parseString(r.body).asJsonObject } catch(_:Exception) { throw ApiException(message="模型目录不是有效 JSON，请检查服务地址") }
            val array=json.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: throw ApiException(message="服务没有返回 data 模型列表，可使用高级手动模型名")
            val models=array.mapNotNull { item ->
                if(!item.isJsonObject) null else {
                    val o=item.asJsonObject; val id=o.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()
                    val modalities=o.getAsJsonObject("architecture")?.get("input_modalities") ?: o.get("input_modalities")
                    val images=modalities?.takeIf { it.isJsonArray }?.asJsonArray?.any { it.isJsonPrimitive && it.asString=="image" }
                    if(id.isNullOrBlank()) null else ApiModel(id,o.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString,images)
                }
            }.distinctBy { it.id }.sortedBy { it.id }
            if(models.isEmpty()) throw ApiException(message="服务返回空模型目录，可检查权限或使用高级手动模型名")
            CatalogResult(profile.revision,models,resolvedBaseUrl=selected?.removeSuffix("/models"))
        } catch(e:CancellationException) { throw e }
        catch(e:IllegalArgumentException) { CatalogResult(profile.revision,error=e.message ?: "配置无效") }
        catch(e:ApiException) { CatalogResult(profile.revision,error=e.message ?: "读取失败") }
    }
    private fun getWithRetry(request:ApiRequest,cancel:Cancellation):ApiResponse {
        for(attempt in 0..1) {
            cancel.check()
            try {
                val response=transport.execute(request,cancel)
                if(attempt==1 || response.status !in setOf(500,502,503,504)) return response
            } catch(e:ApiException) { if(attempt==1 || e.status!=null) throw e }
            try { Thread.sleep(100) } catch(_:InterruptedException) { Thread.currentThread().interrupt(); throw CancellationException() }
            cancel.check()
        }
        throw ApiException(message="读取目录失败")
    }
    companion object {
        fun httpError(status:Int):String = when(status) {
            401 -> "HTTP 401：API Key 无效或已失效"
            403 -> "HTTP 403：服务拒绝访问，请检查模型权限"
            404 -> "HTTP 404：服务未提供模型目录或地址不正确"
            429 -> "HTTP 429：请求限流，请稍后重试"
            else -> "HTTP $status：服务请求失败"
        }
    }
}
