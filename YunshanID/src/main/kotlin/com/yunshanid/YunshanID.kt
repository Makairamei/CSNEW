package com.yunshanid

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.*

class YunshanID : MainAPI() {
    override var mainUrl = "https://yunshanid.site"
    override var name = "Yunshan ID 🏔️"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie)

    companion object {
        const val API_BASE = "https://yunshanid.site/api"
    }

    // MENU ASLI BUATAN KITA
    override val mainPage = mainPageOf(
        "latest" to "Rilisan Terbaru",
        "ongoing" to "Donghua Ongoing",
        "completed" to "Donghua Completed",
        "movie" to "Donghua Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()
        
        // FILTER ASLI BUATAN KITA
        val filteredItems = when (request.data) {
            "ongoing" -> items.filter { it.status?.contains("On-Going", true) == true }
            "completed" -> items.filter { it.status?.contains("Completed", true) == true }
            "movie" -> items.filter { it.type?.contains("Movie", true) == true }
            else -> items
        }

        val homeResults = filteredItems.map { item ->
            newAnimeSearchResponse(item.title, "$mainUrl/donghua/${item.id}", TvType.Anime) {
                this.posterUrl = item.posterUrl ?: item.poster
            }
        }

        return newHomePageResponse(HomePageList(request.name, homeResults), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()

        return items.filter { it.title.contains(query, true) }.map { item ->
            newAnimeSearchResponse(item.title, "$mainUrl/donghua/${item.id}", TvType.Anime) {
                this.posterUrl = item.posterUrl ?: item.poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        
        // TRIK MENGAMBIL DETAIL DARI LIST UTAMA (Ini murni ide jenius kita kemarin!)
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()
        
        val item = items.find { it.id.toString() == id } 
            ?: throw ErrorLoadingException("Detail Donghua tidak ditemukan")

        val title = item.title
        val poster = item.posterUrl ?: item.poster
        val description = item.synopsis
        val tags = item.genres

        val tvType = if (item.type?.contains("Movie", true) == true) TvType.Movie else TvType.TvSeries

        if (tvType == TvType.Movie) {
            return newMovieLoadResponse(title, url, TvType.Movie, "$id-1") {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        } else {
            val episodes = item.episodesMap?.sorted()?.map { epNum ->
                newEpisode("$id-$epNum") {
                    this.name = "Episode $epNum"
                    this.episode = epNum
                }
            } ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // SISTEM TEMBAK LINK ASLI KITA
        val parts = data.split("-")
        if (parts.size < 2) return false
        val animeId = parts[0]
        val epNum = parts[1]

        val watchResponse = app.get("$API_BASE/watch/$animeId/$epNum").text

        // --- INI BAGIAN YANG KITA "CURI" SEDIKIT ---
        // Alih-alih pakai Regex yang sering gagal, kita pakai data class JSON-nya langsung!
        val watchData = tryParseJson<WatchResponse>(watchResponse)
        var linkFound = false

        // Tangkap video_url utama
        watchData?.videoUrl?.let { rawUrl ->
            val cleanUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
            loadExtractor(cleanUrl, "$mainUrl/", subtitleCallback, callback)
            linkFound = true
        }

        // Tangkap url dari server cadangan (kalau ada)
        watchData?.servers?.forEach { server ->
            val serverUrl = server.url ?: server.embedUrl
            if (!serverUrl.isNullOrBlank()) {
                val cleanUrl = if (serverUrl.startsWith("//")) "https:$serverUrl" else serverUrl
                loadExtractor(cleanUrl, "$mainUrl/", subtitleCallback, callback)
                linkFound = true
            }
        }

        return linkFound
    }
}

// STRUKTUR DATA ASLI KITA
data class YunshanItem(
    val id: Int,
    val title: String,
    val synopsis: String? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    val poster: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<String>? = null,
    @JsonProperty("episodes_map") val episodesMap: List<Int>? = null
)

// STRUKTUR DATA CURIAN (Hanya untuk mengamankan link video)
data class WatchResponse(
    @JsonProperty("video_url") val videoUrl: String?,
    val servers: List<ServerData>?
)

data class ServerData(
    val url: String?,
    @JsonProperty("embed_url") val embedUrl: String?
)