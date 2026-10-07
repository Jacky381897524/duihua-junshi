package com.loveadvisor.core.api

data class ApiProfile(val id: String = "default", val baseUrl: String, val apiKey: String, val selectedModelId: String? = null, val allowLanHttp: Boolean = false, val revision: Long = 0, val resolvedBaseUrl: String? = null)
data class ApiModel(val id: String, val name: String? = null, val supportsImages:Boolean? = null)
data class EndpointSet(val modelsCandidates: List<String>, val chatCompletions: String)
data class ApiRequest(val method: String, val url: String, val headers: Map<String,String> = emptyMap(), val body: String? = null, val allowLanHttp: Boolean = false)
data class ApiResponse(val status: Int, val body: String, val headers: Map<String,String> = emptyMap())
data class CatalogResult(val profileRevision: Long, val models: List<ApiModel> = emptyList(), val error: String? = null, val resolvedBaseUrl: String? = null)
data class PromptMessage(val role: String, val content: String)
class ApiException(val status: Int? = null, message: String) : RuntimeException(message)
