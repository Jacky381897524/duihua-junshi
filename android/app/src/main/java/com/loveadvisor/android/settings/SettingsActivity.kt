package com.loveadvisor.android.settings
import android.os.Bundle
import android.text.*
import android.text.InputType
import android.app.AlertDialog
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.core.api.*
import com.loveadvisor.core.advisor.ReplyStyles
import com.loveadvisor.android.capture.ChatCaptureService
class SettingsActivity:BaseActivity() {
    private val drafts=mutableMapOf<String,ApiProfile>()
    private var replyForm:Form?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("模型与连接设置","在聊天应用点读取信息才识别对话；刷新使用已读取的上下文。")
        val original=graph.settings.load(); drafts.putAll(original.providerProfiles)
        val judgeForm=JudgeSettingsForm(this,original);val judge=judgeForm.card;body.addView(judge)
        val reply=Form("回复 · 六条不同方案",false,original.replyProvider,original.profile,original.models).also { replyForm=it }
        val vision=Form("视觉 · 原文与左右归属",true,original.visionProvider,original.vision,original.visionModels)
        val judgeStatus=judgeForm.status
        var judgeBusy=false
        judge.addView(ViewStyles.button(this,"测试判断连接") {
            if(!judgeBusy) {
                val submitted=judgeForm.selectedProfile(ProviderPresets.openRouterKey(reply.profile(),vision.profile()))
                judgeBusy=true; judgeStatus.text="正在测试判断接口…"; val started=android.os.SystemClock.elapsedRealtime()
                work("test-judge",{ cancel -> ConnectionTestClient(JdkHttpTransport(10000,30000)).judge(submitted,cancel) },{ result ->
                    judgeBusy=false
                    val current=judgeForm.selectedProfile(ProviderPresets.openRouterKey(reply.profile(),vision.profile()))
                    judgeStatus.text=if(submitted!=current) "配置已改变，请重新测试" else "✓ ${result.detail} · ${android.os.SystemClock.elapsedRealtime()-started} ms"
                },{ error -> judgeBusy=false; judgeStatus.text="测试失败：$error" })
            }
        })
        judge.addView(ViewStyles.text(this,"测试使用内置示例，会产生少量服务用量。",12f,ViewStyles.MUTED))
        body.addView(ViewStyles.button(this,"保存并返回",true) {
            try {
                val p=reply.profile(); val v=vision.profile(); EndpointResolver().resolve(p); EndpointResolver().resolve(v)
                require(!p.selectedModelId.isNullOrBlank() && !v.selectedModelId.isNullOrBlank()) { "请刷新并选择回复和视觉模型" }
                require(p.apiKey.isNotBlank() && p.apiKey.none { it.isISOControl() }) { "请填写有效的回复 API Key" }
                val judgeKey=judgeForm.defaultApiKey(); require(judgeKey.none { it.isISOControl() }) { "判断密钥无效" }
                judgeForm.validate(ProviderPresets.openRouterKey(p,v))
                drafts["reply:${reply.provider.name}"]=p; drafts["vision:${vision.provider.name}"]=v
                graph.settings.update { it.copy(profile=p,vision=v,models=reply.models,visionModels=vision.models,replyProvider=reply.provider.name,visionProvider=vision.provider.name,providerProfiles=drafts.toMap(),manualModel=false,automaticAnalysis=true,jev=JevProfile(true,ProviderPresets.JEV_ENDPOINT,judgeKey,ProviderPresets.JEV_MODEL),judgeProvider=judgeForm.provider,judgeCustom=judgeForm.customProfile()) }
                graph.controller.invalidate("配置变化"); toast("配置已加密保存"); finish()
            } catch(e:Exception) { toast(graph.safeError(e)) }
        })
    }
    fun refreshJudgeModels(profile:ApiProfile,onModels:(List<ApiModel>)->Unit) {
        work("catalog",{ cancel -> graph.catalog.list(profile,cancel).models },onModels)
    }
    private inner class Form(title:String,val visual:Boolean,initialProvider:String,initial:ApiProfile,initialModels:List<ApiModel>) {
        var provider=runCatching { ProviderPresets.valueOf(initialProvider) }.getOrDefault(ProviderPresets.CUSTOM)
        var models=initialModels; private set
        private var selected=initial.selectedModelId; private var resolved=initial.resolvedBaseUrl
        private var revision=initial.revision; private var allowHttp=initial.allowLanHttp; private var busy=false
        private var testing=false
        private val url=ViewStyles.field(this@SettingsActivity,"服务地址",initial.baseUrl)
        private val key=ViewStyles.field(this@SettingsActivity,"API Key",initial.apiKey).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        private val label=ViewStyles.text(this@SettingsActivity,"",14f,ViewStyles.BLUE)
        private val status=ViewStyles.text(this@SettingsActivity,"",12f,ViewStyles.MUTED)
        private val slot get()=if(visual) "vision" else "reply"
        fun profile()=ApiProfile(id="$slot:${provider.name}",baseUrl=url.text.toString().trim(),apiKey=key.text.toString().trim(),selectedModelId=selected,allowLanHttp=allowHttp,revision=revision,resolvedBaseUrl=resolved)
        private fun effectiveTestProfile():ApiProfile {
            val draft=profile()
            return if(visual && draft.apiKey.isBlank() && ProviderPresets.ownsOpenRouter(draft)) draft.copy(apiKey=ProviderPresets.openRouterKey(replyForm?.profile() ?: graph.settings.load().profile,draft)) else draft
        }
        private fun showSelection() { label.text="已选：${selected ?: "刷新后选择模型"}" }
        init {
            val card=ViewStyles.card(this@SettingsActivity); body.addView(card); card.addView(ViewStyles.text(this@SettingsActivity,title,20f,bold=true))
            val spinner=Spinner(this@SettingsActivity); spinner.adapter=ArrayAdapter(this@SettingsActivity,android.R.layout.simple_spinner_dropdown_item,ProviderPresets.entries.map { it.label }); spinner.setSelection(provider.ordinal); card.addView(spinner)
            card.addView(url); card.addView(key); card.addView(label); card.addView(status); showSelection()
            if(visual) card.addView(ViewStyles.text(this@SettingsActivity,"点击读取时识别文字、图片和表情包；OpenRouter 密钥留空可复用回复接口。不会因聊天变化自动调用模型。",12f,ViewStyles.MUTED))
            val watcher=object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { revision++; resolved=null; status.text="配置已修改" }
                override fun afterTextChanged(s:Editable?) {}
            }; url.addTextChangedListener(watcher); key.addTextChangedListener(watcher)
            spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent:AdapterView<*>?) {}
                override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                    val next=ProviderPresets.entries[position]; if(next==provider) return
                    drafts["$slot:${provider.name}"]=profile(); provider=next
                    val restored=drafts["$slot:${next.name}"] ?: if(visual) next.vision() else next.reply()
                    url.setText(restored.baseUrl); key.setText(restored.apiKey); selected=restored.selectedModelId; resolved=restored.resolvedBaseUrl; allowHttp=restored.allowLanHttp; models=emptyList(); showSelection()
                }
            }
            card.addView(ViewStyles.button(this@SettingsActivity,"刷新模型") {
                if(!busy) {
                    busy=true; val submitted=profile(); status.text="正在读取服务模型目录…"
                    work("catalog",{ cancel -> graph.catalog.list(submitted,cancel) },{ result ->
                        busy=false
                        if(result.profileRevision!=revision) status.text="配置变化，旧目录已丢弃"
                        else if(result.error!=null) status.text=result.error+"。服务未开放目录时，可填写官方模型 ID。"
                        else {
                            models=result.models; resolved=result.resolvedBaseUrl
                            status.text="读取到 ${models.size} 个模型"+(if(selected!=null && models.none { it.id==selected }) "，保留已选模型（本次目录未列出）" else "")
                            if(visual && models.none { it.supportsImages==true }) status.text="目录未标明图片能力，请在兼容入口填写官方视觉模型 ID"
                            showSelection(); choose()
                        }
                    },{ error -> busy=false; status.text=error })
                }
            })
            card.addView(ViewStyles.button(this@SettingsActivity,"选择模型") { choose() })
            card.addView(ViewStyles.button(this@SettingsActivity,if(visual) "测试图片识别" else "测试回复连接") {
                if(!testing) {
                    val submitted=effectiveTestProfile()
                    testing=true; status.text=if(visual) "正在识别内置测试图片…" else "正在请求所选回复模型…"
                    val started=android.os.SystemClock.elapsedRealtime()
                    work(if(visual) "test-vision" else "test-reply",{ cancel ->
                        val client=ConnectionTestClient(JdkHttpTransport(10000,30000))
                        if(visual) client.vision(submitted,ConnectionTestImage.dataUrl(),cancel) else client.reply(submitted,cancel)
                    },{ result -> testing=false; status.text=if(effectiveTestProfile()!=submitted) "配置已改变，请重新测试" else "✓ ${result.detail} · ${android.os.SystemClock.elapsedRealtime()-started} ms" },
                    { error -> testing=false; status.text="测试失败：$error" })
                }
            })
            card.addView(ViewStyles.text(this@SettingsActivity,if(visual) "发送内置示例图片，验证原文与左右归属；测试会产生少量服务用量。" else "发送内置示例文字，验证模型实际可调用；测试会产生少量服务用量。",12f,ViewStyles.MUTED))
            val manual=ViewStyles.field(this@SettingsActivity,"目录不可用时填写官方模型 ID"); card.addView(manual)
            card.addView(ViewStyles.button(this@SettingsActivity,"采用此模型 ID") { val id=manual.text.toString().trim(); if(id.isBlank() || id.any { it.isISOControl() }) toast("模型 ID 无效") else { selected=id; revision++; showSelection() } })
            card.addView(ViewStyles.switch(this@SettingsActivity,"允许局域网 HTTP",allowHttp) { allowHttp=it; revision++; resolved=null })
        }
        private fun choose() {
            if(models.isEmpty()) { toast("请先刷新模型目录"); return }
            val panel=ViewStyles.column(this@SettingsActivity,12); val search=ViewStyles.field(this@SettingsActivity,"搜索模型"); val list=ListView(this@SettingsActivity); panel.addView(search); panel.addView(list,LinearLayout.LayoutParams(-1,ViewStyles.dp(this@SettingsActivity,320f)))
            val choices=models
            var filtered=choices
            fun render() { filtered=choices.filter { it.id.contains(search.text.toString(),true) || it.name.orEmpty().contains(search.text.toString(),true) }; list.adapter=ArrayAdapter(this@SettingsActivity,android.R.layout.simple_list_item_1,filtered.map { it.id+if(!visual) "" else when(it.supportsImages) { true -> " · 支持图片"; false -> " · 仅文字，不能用于视觉"; null -> " · 图片能力未声明，需手动测试" } }) }
            search.addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {}; override fun afterTextChanged(s:Editable?) { render() } })
            val dialog=AlertDialog.Builder(this@SettingsActivity).setTitle("选择模型").setView(panel).setNegativeButton("取消",null).create(); render()
            list.setOnItemClickListener { _,_,position,_ -> val model=filtered[position]; if(visual && model.supportsImages!=true) toast(if(model.supportsImages==false) "服务目录声明此模型只支持文字，可在回复接口使用" else "图片能力未声明，请通过兼容入口填写并测试") else { selected=model.id; revision++; showSelection(); dialog.dismiss() } }; dialog.show()
        }
    }
}
