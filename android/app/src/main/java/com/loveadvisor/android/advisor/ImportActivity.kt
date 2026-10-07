package com.loveadvisor.android.advisor
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import com.loveadvisor.android.ui.*
import com.loveadvisor.android.capture.CapturePreviewActivity
import com.loveadvisor.android.capture.ocr.MlKitOcr
import com.loveadvisor.core.api.Cancellation
import com.loveadvisor.core.advisor.ImportParser
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
class ImportActivity:BaseActivity() {
    private val ocr=MlKitOcr(); private val cancel=Cancellation()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("导入与粘贴","文本最多 1MiB；截图只做本地 OCR，不自动保存或上传原图。所有内容先校正说话方。")
        val text=ViewStyles.field(this,"粘贴聊天记录，可使用 我：／对方：／成员昵称：",multiline=true); body.addView(text)
        body.addView(ViewStyles.button(this,"预览粘贴内容",true) { try { previewText(text.text.toString()) } catch(e:Exception) { toast(graph.safeError(e)) } })
        body.addView(ViewStyles.button(this,"选择 UTF-8 文本文件") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE),61) })
        body.addView(ViewStyles.button(this,"选择聊天截图") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),62) })
    }
    private fun previewText(text:String) {
        val imported=ImportParser().parseUtf8(text); graph.pendingDraft=ConversationDraft(packageName,-1,"导入对话",imported.kind,imported.messages,imported.speakerMappingConfirmed,CaptureSource.IMPORT)
        startActivity(Intent(this,CapturePreviewActivity::class.java))
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data); val uri=data?.data ?: return; if(resultCode!=RESULT_OK) return
        if(requestCode==61) work("capture",{ cancellation ->
            val bytes=contentResolver.openInputStream(uri)?.use { stream -> val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192); while(true) { cancellation.check(); val n=stream.read(buffer); if(n<0) break; require(out.size()+n<=1024*1024) { "单次导入最多 1MiB" }; out.write(buffer,0,n) }; out.toByteArray() } ?: throw IllegalArgumentException("无法读取文件")
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        },{ text -> try { previewText(text) } catch(e:Exception) { toast(graph.safeError(e)) } })
        if(requestCode==62) work("ocr",{ cancellation -> decode(uri,cancellation) },{ bitmap ->
            val bounds=RectI(0,0,bitmap.width,bitmap.height)
            ocr.recognize(bitmap,cancel,{ boxes,error ->
                if(boxes==null) toast(error.orEmpty()) else { graph.pendingDraft=OcrConversationParser().parse(boxes,CaptureGeometry(bounds,packageName=packageName,windowId=-1)); startActivity(Intent(this,CapturePreviewActivity::class.java)) }
            },{ bitmap.recycle() })
        })
    }
    private fun decode(uri:Uri,cancel:Cancellation):Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth>0 && bounds.outHeight>0) { "图片格式无法识别" }
        var sample=1; while(bounds.outWidth/sample>4096 || bounds.outHeight/sample>4096) sample*=2; cancel.check()
        val bitmap=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 }) } ?: throw IllegalArgumentException("无法读取图片")
        val angle=runCatching { contentResolver.openInputStream(uri)?.use { when(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f } } ?: 0f }.getOrDefault(0f)
        if(angle==0f) return bitmap
        val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply { postRotate(angle) },true); if(rotated!==bitmap) bitmap.recycle(); return rotated
    }
    override fun onDestroy() { cancel.cancel(); ocr.close(); super.onDestroy() }
}
