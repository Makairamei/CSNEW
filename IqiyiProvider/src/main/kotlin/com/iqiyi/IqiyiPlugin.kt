package com.iqiyi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IqiyiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(IqiyiProvider())
    }
}