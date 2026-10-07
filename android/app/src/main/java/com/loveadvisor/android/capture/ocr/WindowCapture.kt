package com.loveadvisor.android.capture.ocr
import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import com.loveadvisor.core.api.Cancellation
sealed class CaptureImageResult {
    data class Success(val bitmap:Bitmap):CaptureImageResult()
    data class Failure(val code:Int,val message:String,val protected:Boolean=false):CaptureImageResult()
}
class WindowCapture(private val service:AccessibilityService) {
    fun capture(windowId:Int,cancel:Cancellation,callback:(CaptureImageResult)->Unit) {
        cancel.check()
        // The legacy API returns display pixels; crop them to the same app-window origin
        // used by native records and vision coordinates.
        val legacyFrame=if(Build.VERSION.SDK_INT<34) service.windows.firstOrNull { it.id==windowId }?.let { Rect().apply { it.getBoundsInScreen(this) } } else null
        if(Build.VERSION.SDK_INT<34 && (legacyFrame==null || legacyFrame.isEmpty)) { callback(CaptureImageResult.Failure(-1,"微信窗口范围暂不可用"));return }
        val listener=object:AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result:AccessibilityService.ScreenshotResult) {
                val hardware=result.hardwareBuffer
                val wrapped=Bitmap.wrapHardwareBuffer(hardware,result.colorSpace)
                val bitmap=wrapped?.copy(Bitmap.Config.ARGB_8888,false); wrapped?.recycle(); hardware.close()
                if(cancel.cancelled) bitmap?.recycle()
                else if(bitmap!=null) {
                    val frame=legacyFrame
                    if(frame==null)callback(CaptureImageResult.Success(bitmap))
                    else if(frame.left<0 || frame.top<0 || frame.right>bitmap.width || frame.bottom>bitmap.height) { bitmap.recycle();callback(CaptureImageResult.Failure(-1,"微信窗口范围已变化")) }
                    else {
                        val cropped=Bitmap.createBitmap(bitmap,frame.left,frame.top,frame.width(),frame.height())
                        if(cropped!==bitmap)bitmap.recycle()
                        callback(CaptureImageResult.Success(cropped))
                    }
                } else callback(CaptureImageResult.Failure(-1,"截图转换失败"))
            }
            override fun onFailure(errorCode:Int) {
                if(!cancel.cancelled) callback(CaptureImageResult.Failure(errorCode,if(errorCode==6) "窗口受系统保护，无法自动截图" else "系统暂未允许无障碍窗口截图",errorCode==6))
            }
        }
        if(Build.VERSION.SDK_INT>=34) service.takeScreenshotOfWindow(windowId,service.mainExecutor,listener)
        else service.takeScreenshot(Display.DEFAULT_DISPLAY,service.mainExecutor,listener)
    }
}
