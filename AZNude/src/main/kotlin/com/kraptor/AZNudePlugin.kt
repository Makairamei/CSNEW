// ! Bu araÃ§ @Kraptor123 tarafÄ±ndan | @Cs-GizliKeyif iÃ§in yazÄ±lmÄ±ÅŸtÄ±r.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class AZNudePlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(AZNude())
    }
}
