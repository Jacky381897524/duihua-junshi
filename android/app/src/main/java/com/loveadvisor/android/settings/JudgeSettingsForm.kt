package com.loveadvisor.android.settings
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.android.storage.SettingsData
import com.loveadvisor.core.api.*

class JudgeSettingsForm(private val context:Context,private val host:SettingsFormHost,original:SettingsData,private val refreshModels:(ApiProfile,(List<ApiModel>)->Unit)->Unit,private val ui:ModelFormStyle=ModelFormStyle(context)) {
    val card=ui.card()
    private val source=ui.button("") {}
    private val normal=ViewStyles.column(context)
    private val custom=ViewStyles.column(context)
    private val defaultKey=ui.field("OpenRouter API Key",original.jev.apiKey).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
    private val url=ui.field("服务地址（兼容 OpenAI）",original.judgeCustom.baseUrl)
    private val key=ui.field("自定义判断 API Key",original.judgeCustom.apiKey).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
    private val model=ui.field("判断模型 ID",original.judgeCustom.selectedModelId.orEmpty())
    private var allowHttp=original.judgeCustom.allowLanHttp
    private var directoryRevision=0L
    var provider=if(original.judgeProvider=="CUSTOM")"CUSTOM" else "OPENROUTER";private set
    val status=ui.text("",12f,ViewStyles.MUTED)
    init {
        card.addView(ui.text("判断接口",20f,bold=true));card.addView(source)
        normal.addView(ui.text("默认模型：typesafe/jev-1.13\n判断意图、等级与候选适合程度。",14f,ViewStyles.MUTED));normal.addView(defaultKey)
        normal.addView(ui.text("留空时复用 OpenRouter 回复或视觉接口的密钥。",12f,ViewStyles.MUTED))
        custom.addView(url);custom.addView(key);custom.addView(model)
        custom.addView(ui.button("刷新并选择判断模型") {
            val submitted=customProfile();val revision=++directoryRevision
            refreshModels(submitted) { models ->
                if(provider!="CUSTOM" || submitted!=customProfile() || revision!=directoryRevision)status.text="配置已改变，请重新刷新"
                else if(models.isEmpty())status.text="目录没有模型，请填写模型 ID"
                else host.choose("选择判断模型",models.map { it.id },models.indexOfFirst { it.id==model.text.toString() }) { index ->
                    if(provider=="CUSTOM" && revision==directoryRevision && submitted==customProfile())model.setText(models[index].id)
                }
            }
        })
        custom.addView(ui.toggle("允许局域网 HTTP",allowHttp) { allowHttp=it })
        custom.addView(ui.text("使用兼容 /chat/completions 的接口；先测试再保存。",12f,ViewStyles.MUTED))
        card.addView(normal);card.addView(custom);card.addView(status)
        source.setOnClickListener {
            host.choose("判断服务",listOf("OpenRouter · 默认","自定义"),if(provider=="CUSTOM")1 else 0) { index ->
                provider=if(index==1)"CUSTOM" else "OPENROUTER";directoryRevision++;render();status.text=""
            }
        };render()
    }
    private fun render() {
        source.text="判断服务：${if(provider=="CUSTOM")"自定义" else "OpenRouter"}"
        normal.visibility=if(provider=="OPENROUTER")View.VISIBLE else View.GONE
        custom.visibility=if(provider=="CUSTOM")View.VISIBLE else View.GONE
    }
    fun customProfile()=ApiProfile(baseUrl=url.text.toString().trim(),apiKey=key.text.toString().trim(),selectedModelId=model.text.toString().trim(),allowLanHttp=allowHttp)
    fun defaultApiKey()=defaultKey.text.toString().trim()
    fun selectedProfile(fallback:String):JevProfile {
        if(provider=="CUSTOM") { val p=customProfile();return JevProfile(true,p.baseUrl,p.apiKey,p.selectedModelId.orEmpty(),p.allowLanHttp,true) }
        return JevProfile(true,ProviderPresets.JEV_ENDPOINT,defaultApiKey().ifBlank { fallback },ProviderPresets.JEV_MODEL)
    }
}
