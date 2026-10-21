package com.loveadvisor.android.settings
import android.content.Context
import android.text.*
import android.text.InputType
import android.widget.*
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.api.*
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*

class ModelSettingsView(context:Context,private val graph:AppGraph,private val host:SettingsFormHost,compact:Boolean=false):LinearLayout(context) {
    private val ui=ModelFormStyle(context,compact)
    private val body get()=this
    private val drafts=mutableMapOf<String,ApiProfile>()
    private var replyForm:Form?=null
    private var visionForm:Form?=null
    private var judgeSettings:JudgeSettingsForm?=null
    init {
        orientation=VERTICAL
        val original=graph.settings.load(); drafts.putAll(original.providerProfiles)
        val judgeForm=JudgeSettingsForm(context,host,original,::refreshJudgeModels,ui).also { judgeSettings=it };val judge=judgeForm.card;body.addView(judge)
        val reply=Form("回复 · 六条不同方案",false,original.replyProvider,original.profile,original.models).also { replyForm=it }
        val vision=Form("视觉 · 原文与左右归属",true,original.visionProvider,original.vision,original.visionModels).also { visionForm=it }
        val judgeStatus=judgeForm.status
        var judgeBusy=false
        judge.addView(ui.button("测试判断连接") {
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
        judge.addView(ui.text("测试使用内置示例，会产生少量服务用量。",12f,ViewStyles.MUTED))
        body.addView(ui.button("保存并返回",true) {
            try {
                val p=reply.profile();val v=vision.profile();val judgeKey=judgeForm.defaultApiKey()
                drafts["reply:${reply.provider.name}"]=p; drafts["vision:${vision.provider.name}"]=v
                val candidate=graph.settings.load().copy(profile=p,vision=v,models=reply.models,visionModels=vision.models,replyProvider=reply.provider.name,visionProvider=vision.provider.name,providerProfiles=drafts.toMap(),manualModel=false,automaticAnalysis=true,jev=JevProfile(true,ProviderPresets.JEV_ENDPOINT,judgeKey,ProviderPresets.JEV_MODEL),judgeProvider=judgeForm.provider,judgeCustom=judgeForm.customProfile())
                graph.settings.update { ModelSettingsDraft(candidate).applyTo(it) }
                graph.controller.invalidate("配置变化"); toast("配置已加密保存"); host.close()
            } catch(e:Exception) { Logs.failed(LogStep.CONFIG_SAVE,e);toast(redact(graph.safeError(e))) }
        })
        body.addView(ui.button("返回候选面板 / 取消") { host.close() })
    }
    fun refreshJudgeModels(profile:ApiProfile,onModels:(List<ApiModel>)->Unit) {
        work("catalog-judge",{ cancel -> graph.catalog.list(profile,cancel).also { if(it.error!=null)Logs.end(Logs.parent(),LogState.FAIL,LogReason.INVALID_RESPONSE) }.models },onModels)
    }
    private fun toast(value:String)=host.message(redact(value))
    private fun redact(value:String):String {
        var text=value
        listOfNotNull(replyForm?.profile()?.apiKey,visionForm?.profile()?.apiKey,judgeSettings?.defaultApiKey(),judgeSettings?.customProfile()?.apiKey).filter { it.isNotBlank() }.forEach { text=text.replace(it,"[已隐藏密钥]") }
        return text
    }
    private fun <T> work(id:String,task:(Cancellation)->T,success:(T)->Unit,failure:(String)->Unit={ toast(it) })=host.work(id,task,success) { failure(redact(it)) }
    private inner class Form(title:String,val visual:Boolean,initialProvider:String,initial:ApiProfile,initialModels:List<ApiModel>) {
        var provider=runCatching { ProviderPresets.valueOf(initialProvider) }.getOrDefault(ProviderPresets.CUSTOM)
        var models=initialModels; private set
        private var selected=initial.selectedModelId; private var resolved=initial.resolvedBaseUrl
        private var revision=initial.revision; private var allowHttp=initial.allowLanHttp; private var busy=false
        private var testing=false
        private val url=ui.field("服务地址",initial.baseUrl)
        private val key=ui.field("API Key",initial.apiKey).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        private val label=ui.text("",14f,ViewStyles.BLUE)
        private val status=ui.text("",12f,ViewStyles.MUTED)
        private val slot get()=if(visual) "vision" else "reply"
        fun profile()=ApiProfile(id="$slot:${provider.name}",baseUrl=url.text.toString().trim(),apiKey=key.text.toString().trim(),selectedModelId=selected,allowLanHttp=allowHttp,revision=revision,resolvedBaseUrl=resolved)
        private fun effectiveTestProfile():ApiProfile {
            val draft=profile()
            return if(visual && draft.apiKey.isBlank() && ProviderPresets.ownsOpenRouter(draft)) draft.copy(apiKey=ProviderPresets.openRouterKey(replyForm?.profile() ?: graph.settings.load().profile,draft)) else draft
        }
        private fun showSelection() { label.text="已选：${selected ?: "刷新后选择模型"}" }
        init {
            val card=ui.card(); body.addView(card); card.addView(ui.text(title,20f,bold=true))
            val providerButton=ui.button("服务：${provider.label}") {}
            card.addView(providerButton)
            card.addView(url); card.addView(key); card.addView(label); card.addView(status); showSelection()
            if(visual) card.addView(ui.text("点击读取时识别文字、图片和表情包；OpenRouter 密钥留空可复用回复接口。不会因聊天变化自动调用模型。",12f,ViewStyles.MUTED))
            val watcher=object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { revision++; resolved=null; status.text="配置已修改" }
                override fun afterTextChanged(s:Editable?) {}
            }; url.addTextChangedListener(watcher); key.addTextChangedListener(watcher)
            providerButton.setOnClickListener {
                Logs.event(LogStep.UI_ACTION,reason=LogReason.UI_CHOOSE)
                host.choose("接口服务",ProviderPresets.entries.map { it.label },provider.ordinal) { position ->
                    val next=ProviderPresets.entries[position]
                    if(next!=provider) {
                        drafts["$slot:${provider.name}"]=profile();provider=next
                        val restored=drafts["$slot:${next.name}"] ?: if(visual)next.vision() else next.reply()
                        url.setText(restored.baseUrl);key.setText(restored.apiKey);selected=restored.selectedModelId;resolved=restored.resolvedBaseUrl;allowHttp=restored.allowLanHttp;models=emptyList()
                        providerButton.text="服务：${provider.label}";showSelection()
                    }
                }
            }
            card.addView(ui.button("刷新模型") {
                if(!busy) {
                    busy=true; val submitted=profile(); status.text="正在读取服务模型目录…"
                    work("catalog-$slot",{ cancel -> graph.catalog.list(submitted,cancel) },{ result ->
                        busy=false
                        if(result.profileRevision!=revision) { Logs.end(Logs.parent(),LogState.CANCELLED,LogReason.RESULT_STALE);status.text="配置变化，旧目录已丢弃" }
                        else if(result.error!=null) { Logs.end(Logs.parent(),LogState.FAIL,LogReason.INVALID_RESPONSE);status.text=result.error+"。服务未开放目录时，可填写官方模型 ID。" }
                        else {
                            models=result.models; resolved=result.resolvedBaseUrl
                            status.text="读取到 ${models.size} 个模型"+(if(selected!=null && models.none { it.id==selected }) "，保留已选模型（本次目录未列出）" else "")
                            if(visual && models.none { it.supportsImages==true }) status.text="目录未标明图片能力，请在兼容入口填写官方视觉模型 ID"
                            showSelection(); choose()
                        }
                    },{ error -> busy=false; status.text=error })
                }
            })
            card.addView(ui.button("选择模型") { choose() })
            card.addView(ui.button(if(visual) "测试图片识别" else "测试回复连接") {
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
            card.addView(ui.text(if(visual) "发送内置示例图片，验证原文与左右归属；测试会产生少量服务用量。" else "发送内置示例文字，验证模型实际可调用；测试会产生少量服务用量。",12f,ViewStyles.MUTED))
            val manual=ui.field("目录不可用时填写官方模型 ID"); card.addView(manual)
            card.addView(ui.button("采用此模型 ID") { val id=manual.text.toString().trim(); if(id.isBlank() || id.any { it.isISOControl() }) toast("模型 ID 无效") else { selected=id; revision++; showSelection() } })
            card.addView(ui.toggle("允许局域网 HTTP",allowHttp) { allowHttp=it; revision++; resolved=null })
            card.addView(ui.text("仅自建的 http://192.168… 等本地服务需要开启；HTTPS 官方接口无需开启。HTTP 不加密传输。",12f,ViewStyles.MUTED))
        }
        private fun choose() {
            if(models.isEmpty()) { toast("请先刷新模型目录");return }
            val options=models.toList();val submitted=profile()
            host.choose("选择模型",options.map { model -> model.id+if(!visual)"" else when(model.supportsImages) { true -> " · 支持图片";false -> " · 仅文字";null -> " · 图片能力未声明" } },options.indexOfFirst { it.id==selected }) { index ->
                if(profile()!=submitted)toast("配置已改变，请重新选择模型")
                else {
                    val model=options[index]
                    if(visual && model.supportsImages!=true)toast("图片能力未确认，请通过官方模型 ID 入口填写并测试")
                    else { selected=model.id;revision++;showSelection() }
                }
            }
        }
    }
}

