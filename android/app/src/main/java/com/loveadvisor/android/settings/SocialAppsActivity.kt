package com.loveadvisor.android.settings

import android.content.Intent
import android.os.Bundle
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.core.capture.ChatApps

class SocialAppsActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);page("聊天应用适配","支持列表共用按需读取、视觉识别与草稿填入通道。不同版本布局仍需实际验证；只在聊天页点击读取。")
        body.addView(ViewStyles.text(this,ChatApps.labels.values.joinToString(" · "),14f))
        body.addView(ViewStyles.text(this,"添加其他已安装应用",20f,bold=true))
        work("settings",{
            val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(intent,0).map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }.distinctBy { it.first }.filter { it.first!=packageName && it.first !in ChatApps.labels && !it.first.startsWith("com.android.") && !it.first.startsWith("android.") }.sortedBy { it.second }
        },{ installed ->
            val selected=graph.settings.load().additionalChatApps.toMutableMap()
            installed.forEach { (pkg,name) -> body.addView(ViewStyles.switch(this,name,pkg in selected) { enabled -> if(enabled)selected[pkg]=name else selected.remove(pkg) }) }
            if(installed.isEmpty())body.addView(ViewStyles.text(this,"暂无其他可添加的应用。",14f,ViewStyles.MUTED))
            body.addView(ViewStyles.text(this,"本人昵称（同侧消息布局时使用）",18f,bold=true))
            body.addView(ViewStyles.text(this,"Discord等应用的双方发言在同一侧时，可填写该平台上你自己的准确昵称，帮助区分双方。",12f,ViewStyles.MUTED))
            val names=graph.settings.load().socialSelfNames.toMutableMap()
            val choices=(ChatApps.labels+installed.toMap()).toList()
            val picker=Spinner(this).apply { adapter=ArrayAdapter(this@SocialAppsActivity,android.R.layout.simple_spinner_dropdown_item,choices.map { it.second }) }
            val own=ViewStyles.field(this,"该平台聊天中显示的本人昵称（可空）")
            var current=choices.firstOrNull()?.first
            own.setText(names[current].orEmpty())
            picker.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                    current?.let { names[it]=own.text.toString().trim().take(100) };current=choices[position].first;own.setText(names[current].orEmpty())
                }
            }
            body.addView(picker);body.addView(own)
            body.addView(ViewStyles.button(this,"保存应用范围",true) {
                current?.let { names[it]=own.text.toString().trim().take(100) }
                work("settings",{ graph.settings.update { it.copy(additionalChatApps=selected.toMap(),socialSelfNames=names.filterValues { it.isNotBlank() }) } },{ com.loveadvisor.android.capture.ChatCaptureService.instance?.settingsChanged();toast("应用范围已保存");finish() })
            })
        })
    }
}
