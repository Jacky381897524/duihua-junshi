package com.loveadvisor.android.ui

import android.os.Bundle

class ReleaseHistoryActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page("更新记录")
        body.addView(ViewStyles.button(this, "返回主页") { finish() })
        val history = ViewStyles.card(this)
        history.addView(ReleaseHistoryView(this))
        body.addView(history)
    }
}
