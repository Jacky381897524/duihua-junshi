package com.loveadvisor.android.storage
import com.google.gson.Gson
import com.loveadvisor.core.api.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.loveadvisor.core.overlay.BubbleIdlePolicy
data class SettingsData(val profile:ApiProfile=ProviderPresets.OPENROUTER.reply(),val models:List<ApiModel> = emptyList(),val manualModel:Boolean=false,val overlayEnabled:Boolean=true,val automaticAnalysis:Boolean=true,val reducedMotion:Boolean=false,val historyEnabled:Boolean=false,val jev:JevProfile=JevProfile(true,ProviderPresets.JEV_ENDPOINT,"",ProviderPresets.JEV_MODEL),val vision:ApiProfile=ProviderPresets.OPENROUTER.vision(),val visionModels:List<ApiModel> = emptyList(),val replyProvider:String="OPENROUTER",val visionProvider:String="OPENROUTER",val providerProfiles:Map<String,ApiProfile> = emptyMap(),val replyStyles:Map<String,String> = emptyMap(),val fillPanelBehavior:String="collapse",val replyStyleTitles:Map<String,String> = emptyMap(),val judgeProvider:String="OPENROUTER",val judgeCustom:ApiProfile=ApiProfile(baseUrl="",apiKey=""),val replyStyleApps:Map<String,String> = emptyMap(),val globalReplyStyle:String="",val bubbleAutoHideSeconds:Int=BubbleIdlePolicy.DEFAULT_SECONDS,val nativeIslandEnabled:Boolean=false,val nativeIslandConfirmedBuild:String?=null,val topCapsuleEnabled:Boolean=false,val capsuleOffsetXdp:Float=0f,val capsuleOffsetYdp:Float=0f,val capsuleLocked:Boolean=false,val capsuleScale:Float=1f,val frostedPanelEnabled:Boolean=true,val replyStyleAliases:Map<String,String> = emptyMap(),val groupContextLimit:Int=100,val groupReplyStyles:Map<String,String> = emptyMap(),val groupReplyStyleTitles:Map<String,String> = emptyMap(),val groupReplyStyleApps:Map<String,String> = emptyMap(),val groupReplyStyleAliases:Map<String,String> = emptyMap(),val globalGroupReplyStyle:String="") {
    fun judgeProfile():JevProfile=if(judgeProvider=="CUSTOM") JevProfile(true,judgeCustom.baseUrl,judgeCustom.apiKey,judgeCustom.selectedModelId.orEmpty(),judgeCustom.allowLanHttp,true) else jev.copy(custom=false,apiKey=jev.apiKey.ifBlank { ProviderPresets.openRouterKey(profile,vision) })
}
class AppSettings(private val documents:AndroidDocumentStore) {
    private val gson=Gson()
    private var cached:SettingsData?=null
    @Synchronized fun load():SettingsData=cached ?: (documents.read("settings")?.let { decode(it) } ?: SettingsData()).also { cached=it }
    @Synchronized fun save(value:SettingsData) { documents.write("settings",gson.toJson(value)); cached=value }
    @Synchronized fun update(transform:(SettingsData)->SettingsData) { save(transform(load())) }
    fun replyStyleCatalog(kind:com.loveadvisor.core.model.ChatKind=com.loveadvisor.core.model.ChatKind.DIRECT)=load().let {
        if(kind==com.loveadvisor.core.model.ChatKind.GROUP)com.loveadvisor.core.advisor.ReplyStyleCatalog(it.groupReplyStyles,it.groupReplyStyleTitles,it.groupReplyStyleApps,it.groupReplyStyleAliases)
        else com.loveadvisor.core.advisor.ReplyStyleCatalog(it.replyStyles,it.replyStyleTitles,it.replyStyleApps,it.replyStyleAliases)
    }
    fun saveReplyStyleCatalog(c:com.loveadvisor.core.advisor.ReplyStyleCatalog,kind:com.loveadvisor.core.model.ChatKind=com.loveadvisor.core.model.ChatKind.DIRECT) { update {
        if(kind==com.loveadvisor.core.model.ChatKind.GROUP)it.copy(groupReplyStyles=c.styles,groupReplyStyleTitles=c.titles,groupReplyStyleApps=c.apps,groupReplyStyleAliases=c.aliases)
        else it.copy(replyStyles=c.styles,replyStyleTitles=c.titles,replyStyleApps=c.apps,replyStyleAliases=c.aliases)
    } }
    companion object {
        fun decode(raw:String):SettingsData {
            val gson=Gson(); val merged=gson.toJsonTree(SettingsData()).asJsonObject
            fun merge(to:JsonObject,from:JsonObject) { from.entrySet().forEach { (key,value) ->
                if(!value.isJsonNull) { val current=to[key]; if(value.isJsonObject && current?.isJsonObject==true) merge(current.asJsonObject,value.asJsonObject) else to.add(key,value) }
            } }
            val old=JsonParser.parseString(raw).asJsonObject
            merge(merged,old)
            val loaded=gson.fromJson(merged,SettingsData::class.java)
            val profile=if(loaded.profile.baseUrl.isBlank()) ProviderPresets.OPENROUTER.reply(loaded.profile.apiKey) else loaded.profile
            val replyProvider=if(old.has("replyProvider")) loaded.replyProvider else ProviderPresets.detect(profile).name
            val oldJudge=old.get("jev")?.takeIf { it.isJsonObject }?.asJsonObject
            val oldEndpoint=oldJudge?.get("endpoint")?.takeIf { it.isJsonPrimitive }?.asString
            val judgeKey=if(oldEndpoint!=null && runCatching { java.net.URI(oldEndpoint).host=="openrouter.ai" }.getOrDefault(false).not()) "" else loaded.jev.apiKey
            val migrateIsland= !old.has("topCapsuleEnabled") && loaded.nativeIslandEnabled
            return loaded.copy(groupContextLimit=com.loveadvisor.core.capture.ConversationContextPolicy.limit(com.loveadvisor.core.model.ChatKind.GROUP,loaded.groupContextLimit),capsuleScale=loaded.capsuleScale.takeIf { it.isFinite() }?.coerceIn(.7f,2f) ?: 1f,topCapsuleEnabled=loaded.topCapsuleEnabled || migrateIsland,nativeIslandEnabled=if(migrateIsland)false else loaded.nativeIslandEnabled,capsuleOffsetXdp=loaded.capsuleOffsetXdp.takeIf { it.isFinite() }?.coerceIn(-4096f,4096f) ?: 0f,capsuleOffsetYdp=loaded.capsuleOffsetYdp.takeIf { it.isFinite() }?.coerceIn(-4096f,4096f) ?: 0f,profile=profile,replyProvider=replyProvider,automaticAnalysis=true,bubbleAutoHideSeconds=BubbleIdlePolicy.safeSeconds(loaded.bubbleAutoHideSeconds),jev=loaded.jev.copy(enabled=true,endpoint=ProviderPresets.JEV_ENDPOINT,apiKey=judgeKey,model=ProviderPresets.JEV_MODEL,allowLanHttp=false))
        }
    }
}
