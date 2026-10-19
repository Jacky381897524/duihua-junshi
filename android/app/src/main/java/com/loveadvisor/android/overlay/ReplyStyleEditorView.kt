package com.loveadvisor.android.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.capture.ChatApps
import com.loveadvisor.core.model.ChatKind

/** One in-panel form; object selection is delegated to the same panel's page host. */
class ReplyStyleEditorView(
    context:Context,private val packageName:String,private val observedTitle:String,
    private var catalog:ReplyStyleCatalog,globalStyle:String,
    private val saveCatalog:(ReplyStyleCatalog)->Unit,private val saveGlobal:(String)->Unit,
    private val onClose:()->Unit,
    private val onChooseObject:((List<ReplyStyleEntry>,(ReplyStyleEntry?)->Unit)->Unit)?=null,
    private val kind:ChatKind=ChatKind.DIRECT
):LinearLayout(context) {
    private val palette=PanelPalette(context)
    val nicknameInput=field(if(kind==ChatKind.GROUP)"当前群名，可粘贴校正" else "自动带入当前昵称，可粘贴校正",observedTitle,false)
    val styleInput=field("例如：简短温柔，先接住对方的话，不用标点", "",true)
    val scopeGroup=RadioGroup(context)
    val dedicated=RadioButton(context).apply { id=View.generateViewId();text=if(kind==ChatKind.GROUP)"此群专属" else "当前对话专属" }
    val global=RadioButton(context).apply { id=View.generateViewId();text=if(kind==ChatKind.GROUP)"全部群聊" else "全对象风格" }
    val objectButton=palette.actionButton("关联已有对象",heightDp=44) { chooseObject() }
    val saveButton=palette.actionButton("保存设置",true) { save() }
    private val notice=palette.text("",12f,palette.muted).apply { maxLines=3 }
    private val nicknameLabel=palette.text("${ChatApps.label(packageName)} · 当前${if(kind==ChatKind.GROUP)"群聊" else "对象"}",12f,palette.muted)
    private val matchStatus=palette.text("",12f,palette.blue)
    private var editingKey=catalog.match(observedTitle,packageName)?.key
    private var globalMode=false
    private var globalDraft=globalStyle
    private var dedicatedDraft=catalog.match(observedTitle,packageName)?.style.orEmpty()
    init {
        orientation=VERTICAL
        addView(palette.text("回复风格",19f,palette.ink,true),row(14))
        scopeGroup.orientation=RadioGroup.HORIZONTAL
        for(button in listOf(dedicated,global)) {
            button.buttonDrawable=null;button.gravity=Gravity.CENTER;button.textSize=13f
            button.setPadding(dp(8),dp(10),dp(8),dp(10));button.minimumHeight=dp(44)
            scopeGroup.addView(button,RadioGroup.LayoutParams(0,-2,1f).apply { marginEnd=dp(4) })
        }
        scopeGroup.check(dedicated.id);addView(scopeGroup,row(16))
        addView(nicknameLabel,row(6));addView(nicknameInput,row(6));addView(matchStatus,row(8));addView(objectButton,row(16))
        catalog.match(observedTitle,packageName)?.title?.let { nicknameInput.setText(it) }
        addView(palette.text("希望怎样回复",13f,palette.ink,true),row(7))
        styleInput.setText(dedicatedDraft);addView(styleInput,row(9));addView(notice,row(14))
        addView(saveButton,row(8))
        addView(palette.actionButton("返回候选面板",heightDp=44) { onClose() },row(0))
        scopeGroup.setOnCheckedChangeListener { _,id ->
            if(globalMode)globalDraft=styleInput.text.toString() else dedicatedDraft=styleInput.text.toString()
            globalMode=id==global.id
            for(view in listOf(nicknameInput,nicknameLabel,objectButton,matchStatus))view.visibility=if(globalMode)View.GONE else View.VISIBLE
            styleInput.setText(if(globalMode)globalDraft else dedicatedDraft)
            render();requestLayout()
        }
        render()
    }
    fun selectEntry(entry:ReplyStyleEntry?) {
        val selected=entry ?: catalog.match(observedTitle,packageName)
        editingKey=selected?.key
        nicknameInput.setText(selected?.title ?: observedTitle)
        dedicatedDraft=selected?.style.orEmpty();styleInput.setText(dedicatedDraft);render()
    }
    private fun chooseObject() {
        val choices=catalog.entries().filter { it.packageName==packageName }
        if(onChooseObject!=null)onChooseObject.invoke(choices) { selectEntry(it) }
        else notice.text=if(choices.isEmpty())"还没有已保存对象，填写当前昵称与风格后保存即可。" else "请从面板菜单打开此设置以选择已有对象。"
    }
    private fun row(bottom:Int=10)=LayoutParams(-1,-2).apply { bottomMargin=dp(bottom) }
    private fun dp(n:Int)=ViewStyles.dp(context,n.toFloat())
    private fun field(hintValue:String,value:String,multiline:Boolean)=EditText(context).apply {
        hint=hintValue;setText(value);setTextColor(palette.ink);setHintTextColor(palette.muted);textSize=14f
        setPadding(dp(12),dp(12),dp(12),dp(12));background=ViewStyles.shape(context,palette.card,14f,palette.border)
        minimumHeight=dp(48)
        inputType=InputType.TYPE_CLASS_TEXT or if(multiline)InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI
        if(multiline) { minLines=3;maxLines=5;gravity=Gravity.TOP } else setSingleLine(true)
    }
    private fun render() {
        for(button in listOf(dedicated,global)) {
            val selected=button.isChecked
            button.setTextColor(if(selected)android.graphics.Color.WHITE else palette.muted)
            button.background=ViewStyles.shape(context,if(selected)0xFF3478F4.toInt() else palette.card,12f)
            button.buttonTintList=ColorStateList.valueOf(palette.blue)
        }
        matchStatus.text=when { observedTitle.isBlank() -> "未取得完整昵称，请填写后保存";editingKey!=null -> "已匹配专属风格 · 可直接修改";else -> "当前对象已带入 · 保存后建立专属风格" }
        objectButton.text=if(editingKey==null)"关联已有对象" else "更换关联对象"
        notice.text=when {
            globalMode -> if(kind==ChatKind.GROUP)"没有此群专属风格时使用此设置。" else "未匹配专属风格时使用此设置。"
            observedTitle.isBlank() -> "完整昵称支持特殊文字和组合表情；可粘贴校正。"
            else -> "读取：$observedTitle\n校正后保存会记住匹配；留空风格可取消专属设置。"
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
        catch(_:Exception) { notice.text="未能保存，请重试" }
    }
}
