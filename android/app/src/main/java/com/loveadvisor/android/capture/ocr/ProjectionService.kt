package com.loveadvisor.android.capture.ocr
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.*
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.view.WindowManager
import com.loveadvisor.android.R
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.capture.*
import com.loveadvisor.core.api.Cancellation
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.model.RectI
class ProjectionService:Service() {
    private var projection:MediaProjection?=null; private var display:VirtualDisplay?=null; private var images:ImageReader?=null
    private val main=Handler(Looper.getMainLooper()); private val cancel=Cancellation(); private val ocr=MlKitOcr()
    private var captured=false; private var started=false
    private var lease:ProjectionLease?=null
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(started || intent==null) { stopSelf(); return START_NOT_STICKY }; started=true
        val manager=getSystemService(NotificationManager::class.java); manager.createNotificationChannel(NotificationChannel("capture","本地截图识别",NotificationManager.IMPORTANCE_LOW))
        val notification=Notification.Builder(this,"capture").setSmallIcon(R.drawable.ic_app).setContentTitle("正在读取一次聊天截图").setContentText("仅做本地识别，完成后停止").setOngoing(true).build()
        startForeground(22,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val token=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra("resultData",Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra("resultData")
        if(token==null) { stopSelf(); return START_NOT_STICKY }
        try {
            ChatCaptureService.instance?.beginProjectionCapture()
            val bounds=getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds; val width=bounds.width(); val height=bounds.height()
            val expectedWindow=intent.getIntExtra("windowId",-1)
            val targetPackage=intent.getStringExtra("targetPackage") ?: "com.tencent.mm"
            require(targetPackage in CapturePolicy.supportedPackages && expectedWindow!=-1)
            val currentLease=ProjectionLease(targetPackage,expectedWindow,width,height); lease=currentLease
            projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("resultCode",Activity.RESULT_CANCELED),token)
            projection?.registerCallback(object:MediaProjection.Callback() {
                override fun onStop() { currentLease.cancel(); cancel.cancel(); stopSelf() }
                override fun onCapturedContentResize(w:Int,h:Int) { if(!currentLease.resize(w,h)) { cancel.cancel(); ChatCaptureService.instance?.notifyUser("截图尺寸已变化，请重新授权或导入截图"); stopSelf() } }
            },main)
            val reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2); images=reader
            reader.setOnImageAvailableListener({ source ->
                val image=runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                val service=ChatCaptureService.instance ?: run { image.close(); return@setOnImageAvailableListener }; val window=service.reader.read()
                if(captured || cancel.cancelled || !service.canCapture() || !currentLease.accepts(window)) { image.close(); return@setOnImageAvailableListener }
                val confirmedWindow=window ?: run { image.close(); return@setOnImageAvailableListener }
                val generation=service.captureGeneration()
                val plane=image.planes[0]; val padded=width+(plane.rowStride-plane.pixelStride*width)/plane.pixelStride
                val full=Bitmap.createBitmap(padded,height,Bitmap.Config.ARGB_8888); full.copyPixelsFromBuffer(plane.buffer)
                val bitmap=Bitmap.createBitmap(full,0,0,width,height); if(bitmap!==full) full.recycle(); image.close(); captured=true; display?.release(); display=null
                ocr.recognize(bitmap,cancel,{ boxes,error ->
                    if(boxes!=null && !cancel.cancelled && service.canCapture() && generation==service.captureGeneration() && currentLease.accepts(service.reader.read())) {
                        val inputTop=confirmedWindow.nodes.filter { it.editable && it.bounds.top>confirmedWindow.viewport.top+confirmedWindow.viewport.height*.55 }.minOfOrNull { it.bounds.top }
                        service.previewOcr(OcrConversationParser().parse(boxes,CaptureGeometry(RectI(0,0,width,height),inputTop=inputTop,packageName=confirmedWindow.packageName,windowId=confirmedWindow.windowId)))
                    } else service.notifyUser(error ?: "窗口已变化，请重新读取")
                },{ bitmap.recycle(); stopSelf() })
            },main)
            display=projection?.createVirtualDisplay("LoveAdvisorOnce",width,height,resources.displayMetrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.surface,null,main)
            main.postDelayed({ if(!captured) { ChatCaptureService.instance?.notifyUser("未在微信窗口取得截图，可粘贴原文"); stopSelf() } },12000)
        } catch(_:Exception) { ChatCaptureService.instance?.notifyUser("系统录屏授权不可用，可导入截图或粘贴原文"); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onConfigurationChanged(config:android.content.res.Configuration) { super.onConfigurationChanged(config); lease?.cancel(); cancel.cancel(); stopSelf() }
    override fun onDestroy() { lease?.cancel(); cancel.cancel(); main.removeCallbacksAndMessages(null); display?.release(); images?.close(); projection?.stop(); projection=null; ocr.close(); ChatCaptureService.instance?.endProjectionCapture(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
}
