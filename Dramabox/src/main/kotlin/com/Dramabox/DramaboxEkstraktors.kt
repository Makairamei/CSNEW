package com.Dramabox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app // Wajib import ini buat HTTP GET
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ============================================
// REGION 1: MASTER LINK GENERATOR
// ============================================

object MasterLinkGenerator {
    suspend fun createLink(
        source: String,
        url: String,
        referer: String?,
        quality: Int? = null,
        headers: Map<String, String>? = null
    ): ExtractorLink? {
        val detectedQuality = quality ?: detectQualityFromUrl(url)
        return newExtractorLink(
            source = source,
            name = source,
            url = url,
            type = INFER_TYPE
        ) {
            this.quality = detectedQuality
            if (referer != null) this.referer = referer
            this.headers = headers ?: emptyMap()
        }
    }

    fun detectQualityFromUrl(url: String): Int {
        val urlLower = url.lowercase()
        return when {
            urlLower.contains("2160") || urlLower.contains("4k") -> Qualities.P2160.value
            urlLower.contains("1080") -> Qualities.P1080.value
            urlLower.contains("720") -> Qualities.P720.value
            urlLower.contains("480") -> Qualities.P480.value
            urlLower.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}

// ============================================
// REGION 2: LOAD EXTRACTOR WITH FALLBACK
// ============================================

suspend fun loadExtractorWithFallback(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    var deliveredLinks = 0
    val trackedCallback: (ExtractorLink) -> Unit = { link ->
        deliveredLinks++
        callback(link)
    }

    try {
        if (loadExtractor(url, referer, subtitleCallback, trackedCallback)) return true
    } catch (e: Exception) {
        logError("DramaboxEkstraktors", "loadExtractor failed for url=$url", e)
    }

    // Step 2: Try local extractors
    val urlDomain = url.removePrefix("http://").removePrefix("https://")
        .split("/").first().lowercase()
    val matchingExtractors = DramaboxEkstraktors.list.filter { extractor ->
        urlDomain.contains(
            extractor.mainUrl.removePrefix("http://").removePrefix("https://")
                .split("/").first().lowercase()
        )
    }

    if (matchingExtractors.isEmpty()) return deliveredLinks > 0

    coroutineScope {
        val semaphore = Semaphore(3)
        matchingExtractors.forEach { extractor ->
            launch {
                semaphore.withPermit {
                    try {
                        extractor.getUrl(url, referer, subtitleCallback, trackedCallback)
                    } catch (e: Exception) {
                        logError("DramaboxEkstraktors", "Extractor ${extractor.name} failed for url=$url", e)
                    }
                }
            }
        }
    }
    return deliveredLinks > 0
}

// ============================================
// REGION 3: EXTRACTORS LIST
// ============================================

// INI DIA PENYELAMATNYA BOSKU: Ekstraktor khusus API kamu sendiri!
class DramaboxInternalExtractor : ExtractorApi() {
    override var name = "DramaBox Server"
    override var mainUrl = "https://db.hafizhibnusyam.my.id" // Nangkep link API kamu
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Tembak URL API, lalu urai otomatis ke data class ChapterResponse yang ada di file Dramabox.kt
            val response = app.get(url).parsedSafe<Dramabox.ChapterResponse>()
            
            // Gabungkan array data dan extras
            val allChapters = (response?.data.orEmpty() + response?.extras.orEmpty())
            
            // Ambil data episode (karena API memanggil spesifik ID & episode, harusnya cuma ada 1 target)
            val chapter = allChapters.firstOrNull()
            
            // Looping isi stream_url dan kirim satu-satu ke player
            chapter?.streamUrl?.forEach { stream ->
                val videoUrl = stream.url ?: return@forEach
                val quality = stream.quality ?: Qualities.Unknown.value

                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = this.name, // Nama server yang muncul di player
                        url = videoUrl,
                        referer = "https://www.dramabox.com/in", // Kasih referer utama buat jaga-jaga
                        quality = quality,
                        isM3u8 = videoUrl.contains(".m3u8", ignoreCase = true)
                    )
                )
            }
        } catch (e: Exception) {
            logError("DramaboxInternalExtractor", "Gagal tarik link dari API $url", e)
        }
    }
}

object DramaboxEkstraktors {
    // SEKARANG DAFTARNYA GAK KOSONG LAGI!
    val list = listOf<ExtractorApi>(
        DramaboxInternalExtractor()
    )
}
