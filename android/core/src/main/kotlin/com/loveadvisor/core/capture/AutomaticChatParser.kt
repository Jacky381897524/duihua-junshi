package com.loveadvisor.core.capture

/** Other supported apps use their native window screenshot, avoiding WeChat-specific layouts. */
class AutomaticChatParser {
    private val wechat=AutomaticWeChatParser()
    fun extract(window:WindowSnapshot):CaptureResult=when {
        !ChatApps.supported(window.packageName) || window.viewport.width<=0 || window.viewport.height<=0 -> CaptureResult.NotChat
        window.packageName==ChatApps.WECHAT -> wechat.extract(window)
        else -> CaptureResult.NeedsImage("点击读取后，由视觉确认${ChatApps.label(window.packageName)}原生单聊及消息双方")
    }
}
