package com.loveadvisor.core.api
import com.google.gson.Gson
import com.google.gson.JsonParser

class ChatCompletionClient(private val transport:HttpTransport,private val resolver:EndpointResolver=EndpointResolver()) {
    fun complete(profile:ApiProfile,messages:List<PromptMessage>,cancel:Cancellation,maxTokens:Int?=null):String {
        require(!profile.selectedModelId.isNullOrBlank()) { "请先刷新并选择模型" }
        require(profile.apiKey.isNotBlank()) { "请填写 API Key" }
        require(profile.apiKey.none { it.isISOControl() }) { "API Key 包含无效字符，请重新填写" }
        val payload=mutableMapOf<String,Any?>("model" to profile.selectedModelId,"messages" to messages,"temperature" to 0.7,"stream" to false)
        maxTokens?.let { payload["max_tokens"]=it }
        if(maxTokens!=null && ProviderPresets.detect(profile)==ProviderPresets.DEEPSEEK) payload["thinking"]=mapOf("type" to "disabled")
        if(maxTokens!=null && java.net.URI(profile.baseUrl).host=="openrouter.ai" && profile.selectedModelId=="deepseek/deepseek-chat-v3.1") payload["reasoning"]=mapOf("enabled" to false)
        val body=Gson().toJson(payload)
        val response=transport.execute(ApiRequest("POST",resolver.resolve(profile).chatCompletions,mapOf("Authorization" to "Bearer ${profile.apiKey}"),body,profile.allowLanHttp),cancel)
        if(response.status !in 200..299) throw ApiException(response.status,ModelCatalogClient.httpError(response.status))
        val content=try { JsonParser.parseString(response.body).asJsonObject.getAsJsonArray("choices")[0].asJsonObject.getAsJsonObject("message").get("content").asString } catch(_:Exception) { throw ApiException(message="模型未返回有效的文本回复，请检查协议兼容性") }
        if(content.isBlank()) throw ApiException(message="模型返回空内容")
        return content
    }
}
