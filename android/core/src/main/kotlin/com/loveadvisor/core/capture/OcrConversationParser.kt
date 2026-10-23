package com.loveadvisor.core.capture
import com.loveadvisor.core.model.*
class OcrConversationParser {
    fun parse(boxes:List<OcrTextBox>,geometry:CaptureGeometry,readAtMillis:Long=System.currentTimeMillis()):ConversationDraft {
        val v=geometry.viewport; val bottom=geometry.inputTop ?: (v.bottom-v.height*.13).toInt()
        val nodes=boxes.take(2000).map { NodeRecord(null,it.text,"TextView",false,it.bounds) }
        val title=TextSceneParser.title(nodes,v)
        val messages=boxes.filter { !TextSceneParser.isUi(it.text) && it.bounds.top>=v.top+v.height*.17 && it.bounds.bottom<=bottom }.sortedBy { it.bounds.top }.mapIndexed { index,b -> ChatMessage("ocr-$index",if(geometry.packageName=="com.ss.android.lark") Speaker.UNKNOWN else TextSceneParser.speaker(b.bubble ?: b.bounds,v),b.sender,b.text.trim(),time=MessageTimeParser.before(nodes,b.bounds,v,readAtMillis)) }
        return ConversationDraft(geometry.packageName,geometry.windowId,title,TextSceneParser.kind(title),messages,false,CaptureSource.OCR)
    }
}
