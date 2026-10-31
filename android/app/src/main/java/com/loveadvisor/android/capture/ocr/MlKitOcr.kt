package com.loveadvisor.android.capture.ocr
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.loveadvisor.core.api.Cancellation
import com.loveadvisor.core.capture.OcrTextBox
import com.loveadvisor.core.model.RectI
class MlKitOcr {
    private val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    fun recognize(bitmap:Bitmap,cancel:Cancellation,callback:(List<OcrTextBox>?,String?)->Unit,onFinished:()->Unit={},lines:Boolean=false) {
        cancel.check()
        recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener { text ->
            if(!cancel.cancelled) callback(if(lines)text.textBlocks.flatMap { block -> block.lines.mapNotNull { line -> line.boundingBox?.let { OcrTextBox(line.text,RectI(it.left,it.top,it.right,it.bottom)) } } } else text.textBlocks.mapNotNull { block -> block.boundingBox?.let { OcrTextBox(block.text,RectI(it.left,it.top,it.right,it.bottom)) } },null)
        }.addOnFailureListener { if(!cancel.cancelled) callback(null,"本地中文识别失败，可导入截图或粘贴原文") }.addOnCompleteListener { onFinished() }
    }
    fun close()=recognizer.close()
}
