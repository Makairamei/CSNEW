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

    override val mainPage = mainPageOf(
        "latest" to "Rilisan Terbaru",
        "ongoing" to "Donghua Ongoing",
        "completed" to "Donghua Completed",
        "movie" to "Donghua Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$API_BASE/donghuas").text
        val items = tryParseJson<List<YunshanItem>>(response) ?: emptyList()
        
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
        val parts = data.split("-")
        if (parts.size < 2) return false
        val animeId = parts[0]
        val epNum = parts[1]

        val watchResponse = app.get("$API_BASE/watch/$animeId/$epNum").text
        
        // Bersihkan karakter aneh bawaan JSON (\/ atau \") agar URL terbaca utuh
        val cleanResponse = watchResponse.replace("\\/", "/").replace("\\\"", "\"")

        // REGEX SAPU JAGAT: Menangkap semua teks yang menyerupai URL valid, mengabaikan struktur JSON
        val linkRegex = """(https?:)?//[^\s"'<>\\]+""".toRegex()
        val foundLinks = linkRegex.findAll(cleanResponse).map { it.value }.toList()

        var linkFound = false
        for (rawUrl in foundLinks) {
            // Abaikan link yang hanya berisi gambar atau file website
            if (rawUrl.contains(".jpg") || rawUrl.contains(".png") || rawUrl.contains(".css") || rawUrl.contains(".js")) continue
            
            // Tambahkan "https:" jika terpotong
            val finalUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl

            // 1. Serahkan URL ke mesin Extractor bawaan (Okru, Dailymotion, dll)
            loadExtractor(finalUrl, subtitleCallback, callback)
            linkFound = true
            
            // 2. Jalur Darurat: Jika link ternyata video mentah (.mp4/.m3u8), langsung putar tanpa Extractor
            if (finalUrl.endsWith(".m3u8") || finalUrl.endsWith(".mp4")) {
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = finalUrl,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = finalUrl.endsWith(".m3u8")
                    )
                )
                linkFound = true
            }
        }

        return linkFound
    }
}

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