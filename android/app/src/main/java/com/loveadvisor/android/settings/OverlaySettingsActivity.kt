package com.loveadvisor.android.settings
import android.os.Bundle
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.ui.BaseActivity
class OverlaySettingsActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);page("悬浮窗与顶部胶囊")
        body.addView(OverlaySettingsView(this,graph,{ ChatCaptureService.instance?.settingsChanged();toast("设置已保存");finish() },{ finish() }))
    }
}
