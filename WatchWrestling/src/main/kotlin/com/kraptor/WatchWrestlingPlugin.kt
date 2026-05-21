// ! Bu araÃ§ @Kraptor123 tarafÄ±ndan | @kekikanime iÃ§in yazÄ±lmÄ±ÅŸtÄ±r.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class WatchWrestlingPlugin: Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        registerMainAPI(WatchWrestling())
        registerExtractorAPI(ReklamSiker())
    }
}
