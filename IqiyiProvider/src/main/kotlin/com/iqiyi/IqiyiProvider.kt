package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.Extensions.toQueryParams

class IqiyiProvider : MainAPI() {
    override var mainUrl = "https://www.iq.com"
    override var name = "iQIYI Internasional"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    companion object {
        // Gabungan Reverse Proxy Gateway & Base Routing dari Hasil Bedah Bundel JS
        private const val API_BASE = "https://intl-api.iq.com/3f4/pcw-api.iq.com"
        
        private val baseHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36",
            "Referer" to "https://www.iq.com/",
            "Accept" to "application/json, text/plain, */*"
        )

        // Inject otomatis parameter konstan platformId=3 (PC Web Client)
        private fun getIqiyiParams(extra: Map<String, String> = emptyMap()): String {
            val default = mapOf(
                "platformId" to "3",
                "lang" to "id_id",
                "mod" to "id"
            )
            return (default + extra).toQueryParams()
        }
    }

    // ==================== 1. SEARCH / PENCARIAN ====================
    override suspend fun search(query: String): List<SearchResponse> {
        // Menggunakan endpoint /api/search2 (Variabel R)
        val searchUrl = "$API_BASE/api/search2?${getIqiyiParams(mapOf("k_word" to query, "pageNum" to "1", "pageSize" to "20"))}"
        val rawResponse = app.get(searchUrl, headers = baseHeaders).text
        
        val searchData = parseJson<IqiyiSearchResponse>(rawResponse)
        return searchData.data?.list?.map { item ->
            newTvSeriesSearchResponse(
                name = item.name ?: "",
                url = item.albumIdStr ?: "", // albumIdStr dilempar sebagai acuan load()
                type = TvType.TvSeries
            ) {
                this.posterUrl = item.pic
            }
        } ?: emptyList()
    }

    // ==================== 2. LOAD / DETAIL & EPISODE LIST ====================
    override suspend fun load(url: String): LoadResponse {
        // Menggunakan endpoint /api/v2/episode-list-paging (Variabel s) untuk menarik semua baris eps
        val episodeListUrl = "$API_BASE/api/v2/episode-list-paging?${getIqiyiParams(mapOf("albumId" to url, "page_num" to "1", "page_size" to "100"))}"
        val rawEpisodes = app.get(episodeListUrl, headers = baseHeaders).text
        
        val pageResponse = parseJson<IqiyiEpisodePageResponse>(rawEpisodes)
        
        // Buat list episodenya secara dinamis berdasarkan data array riil dari iQIYI
        val episodes = pageResponse.data?.list?.map { ep ->
            Episode(
                data = ep.qipuIdStr ?: "", // Lempar qipuIdStr unik per episode ke loadLinks()
                name = ep.name ?: "Episode ${ep.order}",
                episode = ep.order ?: 1,
                posterUrl = ep.posterPic
            )
        } ?: emptyList()

        // Ambil judul dan sinopsis dari item pertama atau fallback
        val firstItem = pageResponse.data?.list?.firstOrNull()
        val albumTitle = firstItem?.albumName ?: "iQIYI Series"
        val albumPlot = firstItem?.albumDesc ?: ""
        val albumPoster = firstItem?.albumPic ?: ""

        return newTvSeriesLoadResponse(
            name = albumTitle,
            url = url,
            type = TvType.TvSeries,
            episodes = episodes
        ) {
            this.posterUrl = albumPoster
            this.plot = albumPlot
        }
    }

    // ==================== 3. EXTRACTOR / VIDEO LINK PLAYBACK ====================
    override suspend fun loadLinks(
        data: String, // Berisi qipuIdStr per episode hasil lemparan load()
        isCaster: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Menggunakan endpoint /api/pvvp (Variabel Y - Page Video View Play)
        val vmsUrl = "$API_BASE/api/pvvp?${getIqiyiParams(mapOf("tvid" to data, "bid" to "300"))}"
        val rawPlayerResponse = app.get(vmsUrl, headers = baseHeaders).text
        
        val playerResult = parseJson<IqiyiPlayerResponse>(rawPlayerResponse)
        
        // Ekstraksi fallback Multi-CDN (Edge, Zenlayer, Akamai) yang berhasil kita bedah tempo hari
        playerResult.data?.streamList?.forEach { stream ->
            val videoUrl = stream.secureUrl ?: return@forEach
            val cdnName = stream.cdnProvider ?: "iqiyi_edge"
            val bid = stream.bitrateId ?: 200
            
            val quality = when (bid) {
                100, 200 -> Qualities.P360.value
                300 -> Qualities.P720.value
                500, 600 -> Qualities.P1080.value
                else -> Qualities.Unknown.value
            }

            callback.invoke(
                ExtractorLink(
                    source = "iQIYI - ${cdnName.uppercase()}",
                    name = "iQIYI Stream Player",
                    url = videoUrl,
                    referer = "https://www.iq.com/",
                    quality = quality,
                    isM3u8 = videoUrl.contains(".m3u8") || videoUrl.contains(".mpd")
                )
            )
        }
        return true
    }
}

// ==================== DATA MODELS (JACKSON PARSING) ====================
data class IqiyiSearchResponse(@JsonProperty("data") val data: IqiyiSearchData? = null)
data class IqiyiSearchData(@JsonProperty("list") val list: List<IqiyiSearchItem>? = null)
data class IqiyiSearchItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("albumIdStr") val albumIdStr: String? = null,
    @JsonProperty("pic") val pic: String? = null
)

data class IqiyiEpisodePageResponse(@JsonProperty("data") val data: IqiyiEpisodePageData? = null)
data class IqiyiEpisodePageData(@JsonProperty("list") val list: List<IqiyiEpisodeDetailItem>? = null)
data class IqiyiEpisodeDetailItem(
    @JsonProperty("qipuIdStr") val qipuIdStr: String? = null,
    @JsonProperty("albumName") val albumName: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("albumDesc") val albumDesc: String? = null,
    @JsonProperty("posterPic") val posterPic: String? = null,
    @JsonProperty("albumPic") val albumPic: String? = null,
    @JsonProperty("order") val order: Int? = null
)

data class IqiyiPlayerResponse(@JsonProperty("data") val data: IqiyiPlayerData? = null)
data class IqiyiPlayerData(@JsonProperty("d") val streamList: List<IqiyiCdnStream>? = null)
data class IqiyiCdnStream(
    @JsonProperty("URL") val secureUrl: String? = null,
    @JsonProperty("sp") val cdnProvider: String? = null,
    @JsonProperty("bid") val bitrateId: Int? = null
)