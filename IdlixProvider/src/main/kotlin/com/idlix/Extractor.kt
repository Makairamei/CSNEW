package com.idlix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack

class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace(" ", "%20")
        try {
            val document = app.get(cleanUrl, referer = referer).document
            val html = document.html()

            // LAPIS 1: Ambil m3u8 langsung dari HTML jika tertanam langsung tanpa AJAX
            val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
            val directM3u8 = m3u8Regex.find(html)?.groupValues?.get(1)
            if (directM3u8 != null) {
                generateM3u8(name, directM3u8, cleanUrl).forEach(callback)
                return
            }

            // LAPIS 2: Deteksi Otomatis Endpoint API Player (Mendukung index.php dan ajax.php)
            val hash = cleanUrl.split("/").last().substringAfter("data=")
            val endpoint = if (html.contains("ajax.php")) "$mainUrl/player/ajax.php?data=$hash&do=getVideo" else "$mainUrl/player/index.php?data=$hash&do=getVideo"

            val response = app.post(
                url = endpoint,
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = cleanUrl,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text

            val videoUrlRegex = Regex("""["']videoSource["']\s*:\s*["']([^"']+)["']""")
            val videoUrl = videoUrlRegex.find(response)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(response)?.groupValues?.get(1)?.replace("\\/", "/")

            if (videoUrl != null) {
                val finalUrl = videoUrl.replace(".txt", ".m3u8")
                generateM3u8(name, finalUrl, cleanUrl).forEach(callback)
                return
            }

            // LAPIS 3: Unpack Javascript jika diproteksi packer p,a,c,k,e,d
            if (html.contains("eval(function(p,a,c,k,e,r)")) {
                document.select("script").forEach { script ->
                    val data = script.data()
                    if (data.contains("eval(function(p,a,c,k,e,r)")) {
                        val unpacked = getAndUnpack(data)
                        val unpackedM3u8 = m3u8Regex.find(unpacked)?.groupValues?.get(1)
                        if (unpackedM3u8 != null) {
                            generateM3u8(name, unpackedM3u8, cleanUrl).forEach(callback)
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(name, "Extraction failed for $cleanUrl: ${e.message}")
        }
    }

    data class ResponseSource(
        @JsonProperty("videoSource") val videoSource: String,
    )
}

class Majorplay : ExtractorApi() {
    override var name = "Majorplay"
    override var mainUrl = "https://majorplay.net" // FIX: Hapus wildcard (*) agar URL referer sah
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val domain = "https://" + java.net.URI(url).host
            val document = app.get(url, referer = domain).document
            
            var m3uLink = document.select("source").attr("src").trim()
            if (m3uLink.isEmpty()) {
                val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
                m3uLink = m3u8Regex.find(document.html())?.groupValues?.get(1) ?: ""
            }

            if (m3uLink.isNotEmpty()) {
                generateM3u8(name, m3uLink, domain).forEach(callback)
            }

            // Parsing Subtitles Indonesia / Internasional
            val scripts = document.selectFirst("script:containsData(subtitles)")?.data() ?: return
            val subRegex = Regex("""\\"label\\":\\"([^\\"]*?)\\"[^}]*?\\"path\\":\\"([^\\"]*?)\\"""")

            subRegex.findAll(scripts).forEach { match ->
                val label = match.groupValues[1]
                var vttUrl = match.groupValues[2].replace("\\/", "/")

                if (!vttUrl.startsWith("http")) {
                    vttUrl = domain.trimEnd('/') + "/" + vttUrl.trimStart('/')
                }
                subtitleCallback.invoke(
                    newSubtitleFile(label, vttUrl)
                )
            }
        } catch (e: Exception) {
            Log.e(name, "Extraction failed: ${e.message}")
        }
    }
}