package com.loveadvisor.android.capture
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.loveadvisor.android.capture.ocr.MlKitOcr
import com.loveadvisor.core.api.Cancellation
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
class OcrLifecycleTest {
    @Test fun cancelledRecognitionSuppressesResultsAndAlwaysReleasesBitmap() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val done=CountDownLatch(1); val delivered=AtomicInteger(); val cancel=Cancellation()
        val bitmap=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.WHITE); drawText("聊天识别测试",40f,100f,Paint().apply { color=Color.BLACK; textSize=48f }) }
        lateinit var ocr:MlKitOcr
        instrumentation.runOnMainSync {
            ocr=MlKitOcr()
            ocr.recognize(bitmap,cancel,{ _,_ -> delivered.incrementAndGet() },{ bitmap.recycle(); done.countDown() })
            cancel.cancel()
        }
        try { assertTrue("Bundled OCR did not finish",done.await(30,TimeUnit.SECONDS)); assertEquals(0,delivered.get()); assertTrue(bitmap.isRecycled) }
        finally { instrumentation.runOnMainSync { ocr.close() }; if(!bitmap.isRecycled) bitmap.recycle() }
    }
}
