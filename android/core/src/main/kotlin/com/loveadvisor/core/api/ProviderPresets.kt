package com.loveadvisor.core.api

enum class ProviderPresets(val label:String,val baseUrl:String) {
    OPENROUTER("OpenRouter","https://openrouter.ai/api/v1"),
    DEEPSEEK("DeepSeek","https://api.deepseek.com"),
    QWEN("通义兼容","https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ZHIPU("智谱清言","https://open.bigmodel.cn/api/paas/v4"),
    CUSTOM("自定义","");
    fun reply(key:String="")=ApiProfile(id=name,baseUrl=baseUrl,apiKey=key,selectedModelId=if(this==OPENROUTER) "deepseek/deepseek-chat-v3.1" else null)
    fun vision(key:String="")=ApiProfile(id=name+"-vision",baseUrl=baseUrl,apiKey=key,selectedModelId=if(this==OPENROUTER) "qwen/qwen2.5-vl-72b-instruct" else null)
    companion object {
        const val JEV_MODEL="typesafe/jev-1.13"
        const val JEV_ENDPOINT="https://openrouter.ai/api/alpha/decisions"
        fun detect(profile:ApiProfile):ProviderPresets {
            val host=runCatching { java.net.URI(profile.baseUrl).host }.getOrNull()
            return entries.firstOrNull { it!=CUSTOM && java.net.URI(it.baseUrl).host==host } ?: CUSTOM
        }
        fun ownsOpenRouter(profile:ApiProfile)=detect(profile)==OPENROUTER && (profile.resolvedBaseUrl==null || runCatching { java.net.URI(profile.resolvedBaseUrl).host=="openrouter.ai" }.getOrDefault(false))
        fun openRouterKey(reply:ApiProfile,vision:ApiProfile):String=if(ownsOpenRouter(reply) && reply.apiKey.isNotBlank()) reply.apiKey else if(ownsOpenRouter(vision)) vision.apiKey else ""
        fun visionChoices(models:List<ApiModel>)=models.filter { it.supportsImages==true }
    }
}
