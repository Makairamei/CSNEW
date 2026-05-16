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

    // --- MENU & KATEGORI ASLI BUATAN KAMU ---
    override val mainPage = mainPageOf(
        "latest" to "Rilisan Terbaru",
        "ongoing" to "Donghua Ongoing",
        "completed" to "Donghua Completed",
        "movie" to "Donghua Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()
        
        // --- FILTER ASLI BUATAN KAMU ---
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
        
        // Memakai API Detail agar kita mendapatkan link video yang siap putar
        val response = app.get("$API_BASE/donghua/$id").text
        val detail = tryParseJson<YunshanDetail>(response) 
            ?: throw ErrorLoadingException("Detail Donghua tidak ditemukan")

        val title = detail.title ?: ""
        val poster = detail.posterUrl ?: detail.poster ?: ""
        val description = detail.synopsis
        val tags = detail.genres
        val tvType = if (detail.type?.contains("Movie", true) == true) TvType.Movie else TvType.Anime

        // Membungkus data episode dan link video ke dalam tombol
        val episodes = detail.episodes?.map { ep ->
            newEpisode(ep) {
                this.name = "Episode ${ep.epNumber}"
                this.episode = ep.epNumber
                // INI KUNCI UTAMANYA: Simpan JSON yang berisi link video ke dalam memori tombol
                this.data = mapper.writeValueAsString(ep)
            }
        }?.sortedByDescending { it.episode } ?: emptyList()

        if (tvType == TvType.Movie) {
            // FIX: Untuk Movie, kita harus mengambil data JSON dari episode pertama agar videonya ada
            val movieData = episodes.firstOrNull()?.data ?: ""
            return newMovieLoadResponse(title, url, TvType.Movie, movieData) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
            }
        } else {
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
        // Membaca kembali data JSON dari tombol yang diklik
        val ep = tryParseJson<EpisodeData>(data) ?: return false
        var linkFound = false

        // 1. Jalankan Server Utama (Okru)
        ep.videoUrl?.let { url ->
            val cleanUrl = if (url.startsWith("//")) "https:$url" else url
            loadExtractor(cleanUrl, "$mainUrl/", subtitleCallback, callback)
            linkFound = true
        }

        // 2. Jalankan Server Cadangan
        ep.servers?.forEach { server ->
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

// --- STRUKTUR DATA BASE ---

data class YunshanItem(
    val id: Int,
    val title: String,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    val poster: String? = null,
    val status: String? = null,
    val type: String? = null
)

data class YunshanDetail(
    val id: Int?,
    val title: String?,
    @JsonProperty("poster_url") val posterUrl: String?,
    val poster: String?,
    val synopsis: String?,
    val type: String?,
    val genres: List<String>?,
    val episodes: List<EpisodeData>?
)

data class EpisodeData(
    val id: Int?,
    @JsonProperty("ep_number") val epNumber: Int?,
    @JsonProperty("video_url") val videoUrl: String?,
    val servers: List<ServerData>?
)

data class ServerData(
    val name: String?,
    val url: String?,
    @JsonProperty("embed_url") val embedUrl: String?
)