package com.gojodesu

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GojodesuPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Gojodesu())
        // Kita hanya register yang ada kodenya saja
        registerExtractorAPI(Kotakajaib())
    }
}