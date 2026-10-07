package com.loveadvisor.android.settings
import android.widget.*
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.android.storage.SettingsData
import com.loveadvisor.core.api.*

class JudgeSettingsForm(private val activity:SettingsActivity,original:SettingsData) {
    val card=ViewStyles.card(activity)
    private val source=Spinner(activity)
    private val normal=ViewStyles.column(activity)
    private val custom=ViewStyles.column(activity)
    private val defaultKey=ViewStyles.field(activity,"OpenRouter API Key",original.jev.apiKey).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
    private val url=ViewStyles.field(activity,"服务地址（兼容 OpenAI）",original.judgeCustom.baseUrl)
    private val key=ViewStyles.field(activity,"自定义判断 API Key",original.judgeCustom.apiKey).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
    private val model=ViewStyles.field(activity,"判断模型 ID",original.judgeCustom.selectedModelId.orEmpty())
    private var allowHttp=original.judgeCustom.allowLanHttp
    private var directoryRevision=0L
    var provider=if(original.judgeProvider=="CUSTOM")"CUSTOM" else "OPENROUTER";private set
    val status=ViewStyles.text(activity,"",12f,ViewStyles.MUTED)
    init {
        card.addView(ViewStyles.text(activity,"判断接口",20f,bold=true));card.addView(source)
        source.adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,listOf("OpenRouter · 默认","自定义"))
        normal.addView(ViewStyles.text(activity,"默认模型：typesafe/jev-1.13\n判断意图、风险与六条候选的适合程度。",14f,ViewStyles.MUTED))
        normal.addView(defaultKey);normal.addView(ViewStyles.text(activity,"留空时复用 OpenRouter 回复或视觉接口的密钥。",12f,ViewStyles.MUTED))
        custom.addView(url);custom.addView(key);custom.addView(model)
        custom.addView(ViewStyles.button(activity,"刷新并选择判断模型") { val submitted=customProfile();val revision=++directoryRevision;activity.refreshJudgeModels(submitted) { models ->
            if(provider!="CUSTOM" || submitted!=customProfile() || revision!=directoryRevision)status.text="配置已改变，请重新刷新模型目录"
            else if(models.isEmpty())status.text="目录没有模型，请填写模型 ID"
            else AlertDialog.Builder(activity).setTitle("选择判断模型").setItems(models.map { it.id }.toTypedArray()) { _,which -> if(provider=="CUSTOM" && revision==directoryRevision && submitted==customProfile())model.setText(models[which].id) }.setNegativeButton("取消",null).show()
        } })
        custom.addView(ViewStyles.switch(activity,"允许局域网 HTTP",allowHttp) { allowHttp=it })
        custom.addView(ViewStyles.text(activity,"使用兼容 /chat/completions 的接口。判断结果需包含意图、风险与全部六条评分；测试通过后再保存。",12f,ViewStyles.MUTED))
        card.addView(normal);card.addView(custom);card.addView(status)
        fun visibility() { normal.visibility=if(provider=="OPENROUTER")View.VISIBLE else View.GONE;custom.visibility=if(provider=="CUSTOM")View.VISIBLE else View.GONE }
        source.setSelection(if(provider=="CUSTOM")1 else 0)
        source.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?){}
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { provider=if(position==1)"CUSTOM" else "OPENROUTER";visibility();status.text="" }
        };visibility()
    }
    fun customProfile()=ApiProfile(baseUrl=url.text.toString().trim(),apiKey=key.text.toString().trim(),selectedModelId=model.text.toString().trim(),allowLanHttp=allowHttp)
    fun defaultApiKey()=defaultKey.text.toString().trim()
    fun selectedProfile(fallback:String):JevProfile {
        if(provider=="CUSTOM") { val p=customProfile();return JevProfile(true,p.baseUrl,p.apiKey,p.selectedModelId.orEmpty(),p.allowLanHttp,true) }
        return JevProfile(true,ProviderPresets.JEV_ENDPOINT,defaultApiKey().ifBlank { fallback },ProviderPresets.JEV_MODEL)
    }
    fun validate(fallback:String) {
        val p=selectedProfile(fallback)
        require(p.apiKey.isNotBlank() && p.apiKey.none { it.isISOControl() }) { "请填写判断接口 API Key" }
        if(p.custom) { require(p.model.isNotBlank()) { "请选择或填写自定义判断模型" };EndpointResolver().resolve(customProfile()) }
    }
}
