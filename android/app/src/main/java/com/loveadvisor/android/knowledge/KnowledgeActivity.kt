package com.loveadvisor.android.knowledge
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.app.AlertDialog
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.core.knowledge.KnowledgeDocument
import java.util.UUID
class KnowledgeActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("融合知识库","43 份上游资料 + 聊天副驾规则 + 原创恋爱策略。")
        val search=ViewStyles.field(this,"搜索标题、标签或正文"); body.addView(search)
        body.addView(ViewStyles.button(this,"新增我的笔记") { editNote(null) })
        val results=ViewStyles.column(this); body.addView(results)
        val selected=intent.getStringExtra("id")
        fun render() {
            results.removeAllViews(); val q=search.text.toString()
            val docs=graph.knowledge.documents+graph.profiles.load().notes
            val matches=docs.filter { (selected==null || it.id==selected) && (q.isBlank() || it.title.contains(q,true) || it.tags.any { t -> t.contains(q,true) } || it.text.contains(q,true)) }
            for(doc in matches) {
                val card=ViewStyles.card(this); card.addView(ViewStyles.text(this,doc.title,18f,bold=true)); card.addView(ViewStyles.text(this,doc.source,12f,ViewStyles.MUTED)); card.addView(ViewStyles.button(this,"阅读原文") {
                    val content=ScrollView(this); val text=ViewStyles.text(this,doc.text,15f); text.setTextIsSelectable(true); text.setPadding(24,16,24,16); content.addView(text)
                    val builder=AlertDialog.Builder(this).setTitle(doc.title).setView(content).setPositiveButton("关闭",null)
                    if(doc.source=="user-note") builder.setNeutralButton("编辑") { _,_ -> editNote(doc) }
                    builder.show()
                }); results.addView(card)
            }
            if(matches.isEmpty()) results.addView(ViewStyles.text(this,"没有匹配条目",15f,ViewStyles.MUTED))
        }
        search.addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {}; override fun afterTextChanged(s:Editable?) { render() } }); render()
    }
    private fun editNote(doc:KnowledgeDocument?) {
        val panel=ViewStyles.column(this,16); val title=ViewStyles.field(this,"笔记标题",doc?.title.orEmpty()); val text=ViewStyles.field(this,"笔记内容",doc?.text.orEmpty(),true); val tags=ViewStyles.field(this,"标签：expression,invite,boundary 等",doc?.tags?.joinToString(",").orEmpty()); panel.addView(title); panel.addView(text); panel.addView(tags)
        val dialog=AlertDialog.Builder(this).setTitle("我的笔记").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).setNeutralButton(if(doc==null) "关闭" else "删除",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(title.text.isBlank() || text.text.isBlank() || text.text.length>12000) { toast("请填写标题和内容，单条内容最多 12000 字"); return@setOnClickListener }
                val note=KnowledgeDocument(doc?.id ?: UUID.randomUUID().toString(),title.text.toString(),"user-note",tags.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() },"user-note",text.text.toString())
                val data=graph.profiles.load(); graph.profiles.save(data.copy(notes=data.notes.filter { it.id!=note.id }+note)); dialog.dismiss(); recreate()
            }
            if(doc!=null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { confirm("删除笔记","删除这条笔记？") { val data=graph.profiles.load(); graph.profiles.save(data.copy(notes=data.notes.filter { it.id!=doc.id })); dialog.dismiss(); recreate() } }
        }; dialog.show()
    }
}
