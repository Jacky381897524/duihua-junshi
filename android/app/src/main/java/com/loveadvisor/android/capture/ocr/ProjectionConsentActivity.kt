package com.loveadvisor.android.capture.ocr
import android.os.Bundle
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.loveadvisor.android.ui.*
class ProjectionConsentActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("授权读取一次截图","仅在内存做本地 OCR；确认后返回微信，校正识别内容。每次重新获得系统授权。")
        body.addView(ViewStyles.button(this,"请求系统录屏授权",true) { startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(),70) })
        body.addView(ViewStyles.button(this,"取消，改用粘贴或导入") { finish() })
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==70) {
            if(resultCode==RESULT_OK && data!=null) startForegroundService(Intent(this,ProjectionService::class.java).putExtra("resultCode",resultCode).putExtra("resultData",data).putExtra("windowId",intent.getIntExtra("windowId",-1)).putExtra("targetPackage",intent.getStringExtra("targetPackage") ?: "com.tencent.mm"))
            else toast("未授权，可粘贴原文或导入截图")
            finish()
        }
    }
}
