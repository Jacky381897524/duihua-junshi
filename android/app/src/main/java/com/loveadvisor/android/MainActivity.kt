package com.loveadvisor.android

import android.os.Bundle
import android.content.Intent
import com.loveadvisor.android.ui.*
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.settings.SettingsActivity
import com.loveadvisor.android.settings.ReplyStyleActivity
import com.loveadvisor.android.settings.OverlaySettingsActivity
import com.loveadvisor.android.knowledge.KnowledgeActivity
import com.loveadvisor.android.memory.ProfilesActivity
import com.loveadvisor.android.advisor.*
import com.loveadvisor.core.startup.StartupPhase
import com.loveadvisor.core.startup.StartupStage

class MainActivity : BaseActivity() {
    private var cacheBusy=false
    private var cacheBytes:Long?=null
    private var cacheButton:android.widget.Button?=null
    private var stopStartup:(()->Unit)?=null
    private var ready=false
    override fun onCreate(savedInstanceState: Bundle?) {
        AppStartupTrace.mark(StartupStage.MAIN_CREATE_ENTERED)
        super.onCreate(savedInstanceState)
        val firstDraw=object:android.view.ViewTreeObserver.OnDrawListener {
            private var observed=false
            override fun onDraw() {
                if(observed)return
                observed=true;AppStartupTrace.mark(StartupStage.FIRST_DRAW)
                window.decorView.post {
                    val observer=window.decorView.viewTreeObserver
                    if(observer.isAlive)observer.removeOnDrawListener(this)
                }
            }
        }
        window.decorView.viewTreeObserver.addOnDrawListener(firstDraw)
        val startup=AppStartup.get(this)
        stopStartup=startup.observe { state ->
            ready=state.phase==StartupPhase.READY
            if(ready) { render();ChatCaptureService.instance?.ensureOverlay() }
            else renderStartup(state.phase)
        }
        renderStartup(startup.state.phase);startup.start()
        AppStartupTrace.mark(StartupStage.MAIN_CREATED)
    }
    override fun onResume() { super.onResume();AppStartupTrace.mark(StartupStage.MAIN_RESUMED);if(ready)render() }
    override fun onDestroy() { stopStartup?.invoke();stopStartup=null;super.onDestroy() }
    private fun renderStartup(phase:StartupPhase) {
        page("对话军师","正在准备本地设置")
        val waiting=phase==StartupPhase.IDLE || phase==StartupPhase.LOADING
        body.addView(ViewStyles.text(this,when(phase) {
            StartupPhase.SLOW -> "本地设置读取较慢，页面仍可操作。读取完成后会自动进入主页。"
            StartupPhase.FAILED -> "本地设置暂时未能读取，已有配置和专属风格均保留。可以点击重试。"
            else -> "正在读取本地设置…"
        },16f))
        if(!waiting) {
            body.addView(ViewStyles.text(this,AppStartup.description(this),12f,ViewStyles.MUTED))
            body.addView(ViewStyles.button(this,if(phase==StartupPhase.SLOW)"检查读取状态" else "重试加载",true) { AppStartup.start(this);renderStartup(AppStartup.get(this).state.phase) })
            body.addView(ViewStyles.button(this,"查看系统无障碍状态") { SystemSettingsNavigator(this).accessibility() })
        }
    }
    private fun render() {
        page("对话军师","读懂当下，再决定怎么回应。")
        body.addView(ViewStyles.button(this,"回复风格设置") { open(ReplyStyleActivity::class.java) })
        val hero=ViewStyles.card(this); body.addView(hero)
        hero.addView(ViewStyles.text(this,"一个对话，六种思路。",26f,bold=true)); hero.addView(ViewStyles.text(this,"结合聊天副驾、狗头军师与恋爱大师的知识，给你不同策略的候选回复。",15f,ViewStyles.MUTED))
        hero.addView(ViewStyles.button(this,"连接聊天悬浮窗",true) { open(PermissionsActivity::class.java) })
        hero.addView(ViewStyles.text(this,"无障碍：${AccessibilityConnectionStatus.label(this)}",12f,ViewStyles.MUTED))
        val config=graph.settings.load(); val model=ViewStyles.card(this); body.addView(model); model.addView(ViewStyles.text(this,"你的模型",19f,bold=true)); model.addView(ViewStyles.text(this,config.profile.selectedModelId ?: "填写服务地址与密钥，刷新选择模型",14f,ViewStyles.MUTED)); model.addView(ViewStyles.button(this,"模型与连接设置") { open(SettingsActivity::class.java) })
        body.addView(ViewStyles.switch(this,"启用聊天悬浮窗",config.overlayEnabled) { enabled -> graph.settings.update { it.copy(overlayEnabled=enabled) }; if(!enabled && com.loveadvisor.core.capture.CapturePolicy.invalidateForDisabledCapture(graph.tracker.currentToken)) graph.controller.invalidate(); ChatCaptureService.instance?.settingsChanged() })
        body.addView(ViewStyles.button(this,"悬浮窗设置 · " + if(config.topCapsuleEnabled) "顶部胶囊" else "${config.bubbleAutoHideSeconds}秒后隐藏") { open(OverlaySettingsActivity::class.java) })
        body.addView(ViewStyles.text(this,"多平台单聊与群聊按需读取 · 私聊50条 / 群聊默认100条\n点击悬浮图标展开，再点读取信息；也可在菜单粘贴单句分析。刷新使用已读内容，填入后由你发送。",14f,ViewStyles.MUTED))
        body.addView(ViewStyles.button(this,"聊天应用适配") { open(com.loveadvisor.android.settings.SocialAppsActivity::class.java) })
        body.addView(ViewStyles.switch(this,"减少动效",config.reducedMotion) { enabled -> graph.settings.update { it.copy(reducedMotion=enabled) } })
        body.addView(ViewStyles.button(this,"融合知识库与我的笔记") { open(KnowledgeActivity::class.java) })
        body.addView(ViewStyles.button(this,"真人感与记忆") { open(com.loveadvisor.android.memory.PersonalMemoryActivity::class.java) })
        body.addView(ViewStyles.button(this,"关系资料、记忆与历史") { open(ProfilesActivity::class.java) })
        body.addView(ViewStyles.button(this,"逐轮模拟练习") { open(PracticeActivity::class.java) })
        body.addView(ViewStyles.button(this,"导入聊天 / 本地截图识别") { open(ImportActivity::class.java) })
        body.addView(ViewStyles.button(this,"多对象独立分析与比较") { open(ComparisonActivity::class.java) })
        body.addView(ViewStyles.button(this,"诊断日志") { open(DiagnosticsActivity::class.java) })
        addCacheButton()
        body.addView(ViewStyles.button(this,"关于与许可") { open(AboutActivity::class.java) })
        body.addView(ViewStyles.button(this,"更新记录") { open(ReleaseHistoryActivity::class.java) })
        val author=ViewStyles.card(this);body.addView(author)
        val authorRow=ViewStyles.row(this);author.addView(authorRow)
        val verifiedAuthor=com.loveadvisor.android.security.VerifiedAuthor.load(this)
        val avatarSize=ViewStyles.dp(this,48f)
        val avatar=android.widget.ImageView(this).apply {
            verifiedAuthor.avatar?.let { bytes -> setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)) } ?: setImageResource(R.drawable.advisor_logo_new);scaleType=android.widget.ImageView.ScaleType.CENTER_CROP
            background=ViewStyles.shape(this@MainActivity,0xFFFFE500.toInt(),24f);clipToOutline=true
            contentDescription="作者头像"
        }
        authorRow.addView(avatar,android.widget.LinearLayout.LayoutParams(avatarSize,avatarSize).apply { rightMargin=ViewStyles.dp(this@MainActivity,12f) })
        val authorName=ViewStyles.column(this)
        authorName.addView(ViewStyles.text(this,"作者",12f,ViewStyles.MUTED))
        authorName.addView(ViewStyles.text(this,verifiedAuthor.name,17f,bold=true))
        authorName.addView(ViewStyles.text(this,if(verifiedAuthor.official)"官方作者资料 · 签名已验证" else "未通过官方签名验证",11f,ViewStyles.MUTED))
        authorRow.addView(authorName,android.widget.LinearLayout.LayoutParams(0,-2,1f))
        authorRow.addView(ViewStyles.text(this,BuildConfig.VERSION_NAME,12f,ViewStyles.MUTED))
    }
    private fun cacheLabel()=if(cacheBusy) "正在清理缓存…" else "清除缓存"+cacheBytes?.let { " · "+android.text.format.Formatter.formatFileSize(this,it) }.orEmpty()
    private fun updateCacheButton() { cacheButton?.apply { text=cacheLabel();isEnabled=!cacheBusy } }
    private fun addCacheButton() {
        val button=ViewStyles.button(this,cacheLabel()) { clearCache() }
        button.contentDescription="清除临时缓存，保留密钥、模型配置、回复风格、关系记忆和聊天历史"
        cacheButton=button;body.addView(button);updateCacheButton()
        if(!cacheBusy)work("cache_usage",{ cancel -> graph.cache.usage { cancel.check() } },{ usage ->
            if(cacheButton===button && !cacheBusy) { cacheBytes=usage.bytes;updateCacheButton() }
        },{ /* Cache statistics are optional; clearing remains available. */ })
    }
    private fun clearCache() {
        if(cacheBusy)return
        cacheBusy=true;updateCacheButton()
        work("cache_clear",{ cancel -> graph.cache.clear { cancel.check() } },{ result ->
            cacheBusy=false;cacheBytes=result.remainingBytes;updateCacheButton()
            val freed=android.text.format.Formatter.formatFileSize(this,result.freedBytes)
            val left=android.text.format.Formatter.formatFileSize(this,result.remainingBytes)
            toast(if(result.failedEntries>0) "已释放 $freed，剩余可统计缓存 $left；部分文件暂时无法清理，配置和历史已保留"
                else "缓存清理完成，已释放 $freed，剩余 $left；配置和历史已保留")
        },{ message -> cacheBusy=false;updateCacheButton();toast("缓存清理未完成：$message") })
    }
}
