package com.loveadvisor.android.overlay

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.capture.ChatApps

/** Edits encrypted local settings through callbacks without opening an Activity. */
class ReplyStyleEditorView(
    context:Context,private val packageName:String,private val observedTitle:String,
    private var catalog:ReplyStyleCatalog,globalStyle:String,
    private val saveCatalog:(ReplyStyleCatalog)->Unit,private val saveGlobal:(String)->Unit,
    private val onClose:()->Unit
):LinearLayout(context) {
    private val palette=PanelPalette(context)
    val nicknameInput=field("完整昵称，可粘贴特殊文字或表情",observedTitle,false)
    val styleInput=field("例如：简短、温柔、不使用标点", "",true)
    val scopeGroup=RadioGroup(context)
    val dedicated=RadioButton(context).apply { id=View.generateViewId();text="当前对话专属";setTextColor(palette.ink) }
    val global=RadioButton(context).apply { id=View.generateViewId();text="全对象风格";setTextColor(palette.ink) }
    val savedObjects=Spinner(context)
    val saveButton=palette.button("保存并返回",true) { save() }
    private val notice=palette.text("",12f,palette.muted)
    private val nicknameLabel=palette.text("${ChatApps.label(packageName)} · 对象昵称",13f,palette.ink,true)
    private val choicesLabel=palette.text("添加专属风格，或选择已有对象关联",12f,palette.muted)
    private var editingKey=catalog.match(observedTitle,packageName)?.key
    private var globalMode=false
    private var globalDraft=globalStyle
    private var dedicatedDraft=catalog.match(observedTitle,packageName)?.style.orEmpty()
    init {
        orientation=VERTICAL
        addView(palette.text("回复风格设置",18f,palette.ink,true),row())
        scopeGroup.orientation=RadioGroup.VERTICAL
        scopeGroup.addView(dedicated);scopeGroup.addView(global);scopeGroup.check(dedicated.id)
        addView(scopeGroup,row())
        addView(nicknameLabel,row());addView(nicknameInput,row())
        addView(choicesLabel,row());addView(savedObjects,row())
        val suggested=catalog.suggestions(observedTitle,packageName).map { it.key }.toSet()
        val entries=catalog.entries().filter { it.packageName==packageName }.sortedBy { it.key !in suggested }
        savedObjects.adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,listOf("添加 / 当前对象")+entries.map { it.title ?: "旧版对象 · ${it.key.take(8)}" })
        val initial=entries.indexOfFirst { it.key==editingKey }.let { if(it<0)0 else it+1 }
        savedObjects.setSelection(initial,false)
        catalog.match(observedTitle,packageName)?.title?.let { nicknameInput.setText(it) }
        styleInput.setText(dedicatedDraft)
        var selected=initial
        savedObjects.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?) {}
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) {
                if(selected==position)return
                selected=position
                val entry=entries.getOrNull(position-1)
                editingKey=entry?.key;nicknameInput.setText(entry?.title ?: observedTitle)
                dedicatedDraft=entry?.style.orEmpty();styleInput.setText(dedicatedDraft);showNotice()
            }
        }
        addView(palette.text("回复风格",13f,palette.ink,true),row())
        addView(styleInput,row());addView(notice,row())
        addView(saveButton,row())
        addView(palette.button("返回候选面板") { onClose() },row())
        scopeGroup.setOnCheckedChangeListener { _,id ->
            if(globalMode)globalDraft=styleInput.text.toString() else dedicatedDraft=styleInput.text.toString()
            globalMode=id==global.id
            for(view in listOf(nicknameInput,nicknameLabel,choicesLabel,savedObjects))view.visibility=if(globalMode)View.GONE else View.VISIBLE
            styleInput.setText(if(globalMode)globalDraft else dedicatedDraft);showNotice();requestLayout()
        }
        showNotice()
    }
    private fun row()=LayoutParams(-1,-2).apply { bottomMargin=dp(10) }
    private fun dp(n:Int)=ViewStyles.dp(context,n.toFloat())
    private fun field(hintValue:String,value:String,multiline:Boolean)=EditText(context).apply {
        hint=hintValue;setText(value);setTextColor(palette.ink);setHintTextColor(palette.muted);textSize=14f
        setPadding(dp(10),dp(9),dp(10),dp(9));background=ViewStyles.shape(context,palette.card,12f,palette.border)
        inputType=InputType.TYPE_CLASS_TEXT or if(multiline)InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI
        if(multiline) { minLines=3;maxLines=5;gravity=Gravity.TOP } else setSingleLine(true)
    }
    private fun showNotice() {
        notice.text=when {
            globalMode -> "没有匹配到专属风格的对象使用此风格。保存后在下一次生成或刷新时生效。"
            observedTitle.isBlank() -> "未能完整识别当前昵称，请粘贴或填写完整昵称后保存；也可先设置全对象风格。"
            else -> "本次读取昵称：$observedTitle\n如有漏字、符号或表情错误，请校正或选择已有对象。保存时会记住本次识别结果。留空风格可删除该对象的专属设置。"
        }
    }
    private fun save() {
        try {
            if(globalMode)saveGlobal(styleInput.text.toString().trim().take(1500))
            else {
                val name=nicknameInput.text.toString().trim()
                require(NicknameText.valid(name)) { "请填写完整昵称，最多512个字符" }
                val style=styleInput.text.toString()
                var updated=catalog.save(name,style,editingKey,packageName)
                if(style.isNotBlank() && observedTitle.isNotBlank() && NicknameText.canonical(name)!=NicknameText.canonical(observedTitle)) {
                    val key=updated.match(name,packageName)?.key ?: error("未能保存对象")
                    updated=updated.bindAlias(observedTitle,key,packageName)
                }
                saveCatalog(updated);catalog=updated
            }
            onClose()
        } catch(error:IllegalArgumentException) { notice.text=error.message ?: "昵称或风格无效" }
        catch(_:Exception) { notice.text="未能保存，请重试；原有设置仍保留" }
    }
}
