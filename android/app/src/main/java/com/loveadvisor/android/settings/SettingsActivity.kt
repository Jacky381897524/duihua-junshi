package com.loveadvisor.android.settings
import android.os.Bundle
import android.view.View
import com.loveadvisor.android.ui.BaseActivity
import com.loveadvisor.core.api.Cancellation

class SettingsActivity:BaseActivity() {
    private lateinit var scope:SettingsWorkScope
    private var selector:View?=null
    private lateinit var form:ModelSettingsView
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);page("模型与连接设置","回复、视觉与判断接口，选择和保存共用同一份配置。")
        scope=SettingsWorkScope(graph)
        val host=object:SettingsFormHost {
            override fun choose(title:String,items:List<String>,selected:Int,onSelected:(Int)->Unit) {
                if(selector!=null)return
                form.visibility=View.GONE
                val picker=ModelPickerView(this@SettingsActivity,title,items,selected,{ index -> closeSelector();onSelected(index) },{ closeSelector() })
                selector=picker;body.addView(picker)
            }
            override fun <T> work(id:String,task:(Cancellation)->T,onSuccess:(T)->Unit,onFailure:(String)->Unit)=scope.work(id,task,onSuccess,onFailure)
            override fun message(text:String)=toast(text)
            override fun close()=finish()
        }
        form=ModelSettingsView(this,graph,host);body.addView(form)
    }
    private fun closeSelector() { selector?.let { body.removeView(it) };selector=null;form.visibility=View.VISIBLE }
    @Deprecated("Legacy back fallback")
    override fun onBackPressed() { if(selector!=null)closeSelector() else super.onBackPressed() }
    override fun onDestroy() { if(::scope.isInitialized)scope.close();super.onDestroy() }
}
