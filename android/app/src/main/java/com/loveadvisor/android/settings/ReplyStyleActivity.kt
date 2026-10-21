package com.loveadvisor.android.settings

import android.os.Bundle
import android.widget.LinearLayout
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.ui.*
import com.loveadvisor.core.advisor.ReplyStyleCatalog
import com.loveadvisor.core.capture.ChatApps

class ReplyStyleActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        page("回复风格设置","统一设置或按对象保存；六条候选和刷新都应用表达偏好。")
        val globalCard=ViewStyles.card(this);body.addView(globalCard)
        globalCard.addView(ViewStyles.text(this,"全对象回复风格",20f,bold=true))
        globalCard.addView(ViewStyles.text(this,"无需填写联系人名称；没有匹配到专属风格时使用此设置。",13f,ViewStyles.MUTED))
        globalCard.addView(ViewStyles.text(this,"有对象专属风格时优先使用专属风格；没有时应用全对象风格。",13f,ViewStyles.MUTED))
        val globalStyle=ViewStyles.field(this,"例如：没有标点符号、简短一点、浪漫一点",graph.settings.load().globalReplyStyle,multiline=true)
        globalCard.addView(globalStyle)
        globalCard.addView(ViewStyles.button(this,"保存全对象回复风格",true) {
            val value=globalStyle.text.toString().trim().take(1500)
            graph.settings.update { it.copy(globalReplyStyle=value) };globalStyle.setText(value)
            toast(if(value.isEmpty())"已清空全对象风格" else "已保存，下次读取或刷新时生效")
        })
        globalCard.addView(ViewStyles.button(this,"清空全对象风格") { graph.settings.update { it.copy(globalReplyStyle="") };globalStyle.setText("");toast("已清空全对象风格") })
        val editor=ViewStyles.card(this); body.addView(editor)
        editor.addView(ViewStyles.text(this,"添加或编辑对象风格",19f,bold=true))
        val platforms=ChatApps.packages.toList()
        val platform=android.widget.Spinner(this).apply { adapter=android.widget.ArrayAdapter(this@ReplyStyleActivity,android.R.layout.simple_spinner_dropdown_item,platforms.map { ChatApps.label(it) }) }
        platform.setSelection(platforms.indexOf(intent.getStringExtra("targetPackage") ?: ChatCaptureService.instance?.currentChatSnapshot?.packageName).coerceAtLeast(0))
        var previousPlatform=platforms[platform.selectedItemPosition]
        editor.addView(platform)
        val contact=ViewStyles.field(this,"聊天顶部显示的对象备注名",intent.getStringExtra("contactTitle") ?: ChatCaptureService.instance?.currentChatSnapshot?.let { it.replyStyleTitle ?: it.identity?.displayTitle }.orEmpty())
        val style=ViewStyles.field(this,"例如：没有标点符号、简短一点、浪漫一点",multiline=true)
        editor.addView(contact); editor.addView(style)
        editor.addView(ViewStyles.text(this,"对象名需与聊天顶部一致。同一平台同名对象请设置不同备注。",12f,ViewStyles.MUTED))
        var editingKey:String?=null
        fun catalog():ReplyStyleCatalog = graph.settings.replyStyleCatalog()
        fun saveCatalog(c:ReplyStyleCatalog) { graph.settings.saveReplyStyleCatalog(c) }
        val saved=ViewStyles.column(this)
        fun renderList() {
            saved.removeAllViews()
            val entries=catalog().entries()
            saved.addView(ViewStyles.text(this,"已保存对象 · ${entries.size}",20f,bold=true))
            if(entries.isEmpty())saved.addView(ViewStyles.text(this,"尚未保存对象风格",14f,ViewStyles.MUTED))
            entries.forEach { entry ->
                val card=ViewStyles.card(this); saved.addView(card)
                val title=entry.title ?: "旧版对象 · ${entry.key.take(8)}"
                card.addView(ViewStyles.text(this,"${ChatApps.label(entry.packageName)} · $title",17f,bold=true))
                card.addView(ViewStyles.text(this,entry.style,14f).apply { maxLines=3 })
                if(entry.title==null)card.addView(ViewStyles.text(this,"旧版未保存对象名，风格仍保留。查看后填写对应备注名并保存即可补全。",12f,ViewStyles.MUTED))
                val actions=ViewStyles.row(this); card.addView(actions)
                actions.addView(ViewStyles.button(this,"查看 / 编辑") {
                    editingKey=entry.key;contact.setText(entry.title.orEmpty());style.setText(entry.style)
                    previousPlatform=entry.packageName
                    platform.setSelection(platforms.indexOf(entry.packageName).coerceAtLeast(0))
                    (body.parent as? android.widget.ScrollView)?.smoothScrollTo(0,0)
                }.apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=ViewStyles.dp(this@ReplyStyleActivity,8f) } })
                actions.addView(ViewStyles.button(this,"删除") {
                    saveCatalog(catalog().remove(entry.key))
                    if(editingKey==entry.key) { editingKey=null;contact.setText("");style.setText("") }
                    renderList();toast(if(graph.settings.load().globalReplyStyle.isNotBlank())"已删除专属风格，改用全对象风格" else "该对象已恢复正常回复")
                }.apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
            }
        }
        editor.addView(ViewStyles.button(this,"保存回复风格",true) {
            val name=contact.text.toString().trim()
            if(name.isEmpty())toast("请填写聊天对象的备注名")
            else {
                try {
                    saveCatalog(catalog().save(name,style.text.toString(),editingKey,platforms[platform.selectedItemPosition]));editingKey=null
                    renderList();toast(if(style.text.toString().isBlank())if(graph.settings.load().globalReplyStyle.isNotBlank())"已删除专属风格，改用全对象风格" else "该对象已恢复正常回复" else "已保存，下次读取或刷新时生效")
                } catch(error:IllegalArgumentException) { com.loveadvisor.android.diagnostics.DiagnosticLogs.failed(com.loveadvisor.core.diagnostics.LogStep.CONFIG_SAVE,error);toast(error.message ?: "请检查昵称") }
            }
        })
        val selectedPlatform=platforms[platform.selectedItemPosition]
        val initial=catalog().rememberTitle(contact.text.toString(),selectedPlatform);saveCatalog(initial)
        initial.match(contact.text.toString(),selectedPlatform)?.let { editingKey=it.key;style.setText(it.style) }
        platform.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                val selected=platforms[position]
                if(selected==previousPlatform)return
                previousPlatform=selected
                val entry=catalog().match(contact.text.toString(),selected)
                editingKey=entry?.key;style.setText(entry?.style.orEmpty())
            }
        }
        body.addView(saved);renderList()
        val preferences=ViewStyles.card(this);body.addView(preferences)
        preferences.addView(ViewStyles.text(this,"填入后面板行为",17f,bold=true))
        val behavior=android.widget.RadioGroup(this)
        val collapse=android.widget.RadioButton(this).apply { id=android.view.View.generateViewId();text="自动收起" }
        val keep=android.widget.RadioButton(this).apply { id=android.view.View.generateViewId();text="保持展开" }
        behavior.addView(collapse);behavior.addView(keep)
        behavior.check(if(graph.settings.load().fillPanelBehavior=="keep")keep.id else collapse.id)
        behavior.setOnCheckedChangeListener { _,selected -> graph.settings.update { it.copy(fillPanelBehavior=if(selected==keep.id)"keep" else "collapse") } }
        preferences.addView(behavior)
    }
}
