package com.shortmax

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class ShortMax : MainAPI() {
    override var mainUrl = "https://www.shorttv.live"
    override var name = "ShortMax 📱"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    companion object {
        private const val BASE_API_URL = "https://api.shorttv.live" 
        
        const val ENDPOINT_RECOMMEND = "$BASE_API_URL/gapi/v1/movie/recommendList"
        const val ENDPOINT_SEARCH = "$BASE_API_URL/gapi/v1/movie/search"
        const val ENDPOINT_DETAIL = "$BASE_API_URL/gapi/v1/movie/detail"
        const val ENDPOINT_VIDEO = "$BASE_API_URL/gapi/v1/movie/episodePlayInfo"

        // Headers hasil sadapan mode web kamu
        fun getWebHeaders(): Map<String, String> {
            val currentTime = System.currentTimeMillis().toString()
            return mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/json",
                "s-platform" to "3",
                "s-language" to "id",
                "s-version" to "1.0.0",
                "s-channel" to "web",
                "s-timestamp" to currentTime,
                "Origin" to "https://www.shorttv.live",
                "Referer" to "https://www.shorttv.live/"
            )
        }

        // 🛡️ REGEX ENGINE: Pemanen Data Cadangan (Anti Gagal Struktur JSON)
        fun harvestItemsWithRegex(rawText: String): List<ShortPlayItem> {
            val items = mutableListOf<ShortPlayItem>()
            // Mencari pola shortPlayId, name, dan cover di dalam teks mentah
            val pattern = """\"shortPlayId\"\s*:\s*(\d+)[^}]*?\"name\"\s*:\s*\"([^\"]+)\"[^}]*?\"cover\"\s*:\s*\"([^\"]+)\"""".toRegex()
            
            pattern.findAll(rawText).forEach { match ->
                val id = match.groups[1]?.value?.toIntOrNull()
                val name = match.groups[2]?.value
                val cover = match.groups[3]?.value?.replace("\\/", "/") // Bersihkan escape slash URL
                if (id != null && !name.isNullOrBlank()) {
                    items.add(ShortPlayItem(shortPlayId = id, name = name, cover = cover))
                }
            }
            return items
        }
    }

    override val mainPage = mainPageOf(
        ENDPOINT_RECOMMEND to "Rekomendasi Utama"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val responseText = app.post(
            request.data, 
            headers = getWebHeaders(),
            json = mapOf("page" to page, "pageSize" to 20)
        ).text
        
        // Coba pakai Parser Formal (Jackson)
        val json = tryParseJson<ShortPlayListResponse>(responseText)
        var rawItems = json?.results ?: json?.data?.results ?: json?.data?.list ?: emptyList()

        // 🚨 FALLBACK: Jika Jackson gagal/kosong, aktifkan Pemanen Teks Mentah (Regex)!
        if (rawItems.isEmpty()) {
            rawItems = harvestItemsWithRegex(responseText)
        }

        val homeResults = rawItems.mapNotNull { item ->
            val id = item.shortPlayId ?: return@mapNotNull null
            newTvSeriesSearchResponse(item.name.orEmpty(), id.toString(), TvType.AsianDrama) {
                this.posterUrl = item.cover
            }
        }.distinctBy { it.url }

        val hasNextPage = json?.isEnd == false || json?.data?.isEnd == false || homeResults.isNotEmpty()
        return newHomePageResponse(HomePageList(request.name, homeResults), hasNextPage)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val responseText = app.post(
            ENDPOINT_SEARCH,
            headers = getWebHeaders(),
            json = mapOf("keyword" to cleanQuery, "page" to 1, "pageSize" to 20)
        ).text
        
        val json = tryParseJson<ShortPlayListResponse>(responseText)
        var rawItems = json?.results ?: json?.data?.results ?: json?.data?.list ?: emptyList()

        // 🚨 FALLBACK PENCARIAN:
        if (rawItems.isEmpty()) {
            rawItems = harvestItemsWithRegex(responseText)
        }

        return rawItems.mapNotNull { item ->
            val id = item.shortPlayId ?: return@mapNotNull null
            newTvSeriesSearchResponse(item.name.orEmpty(), id.toString(), TvType.AsianDrama) {
                this.posterUrl = item.cover
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val playId = url.trim()
        val playIdInt = playId.toIntOrNull() ?: throw ErrorLoadingException("ID Drama Malformed")

        val responseText = app.post(
            ENDPOINT_DETAIL,
            headers = getWebHeaders(),
            json = mapOf("shortPlayId" to playIdInt)
        ).text
        
        val detailData = tryParseJson<ShortPlayDetailResponse>(responseText)?.data 
            ?: tryParseJson<ShortPlayDetailAltResponse>(responseText)?.data
            ?: throw ErrorLoadingException("Gagal Memuat Detail Konten")

        val title = detailData.shortPlayName?.takeIf { it.isNotBlank() } ?: "ShortDrama"
        val poster = detailData.picUrl
        val plotSummary = detailData.summary
        val totalEp = detailData.totalEpisodes ?: 1

        val episodes = (1..totalEp).map { epNum ->
            val loadDataPayload = EpisodePayload(playId = playIdInt, episodeNum = epNum).toJsonString()
            newEpisode(loadDataPayload) {
                this.name = "Episode $epNum"
                this.episode = epNum
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = plotSummary
            this.tags = detailData.labelResponseList.orEmpty().mapNotNull { it.labelName }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val payload = parseJson<EpisodePayload>(data)
        val playId = payload.playId ?: return false
        val epNum = payload.episodeNum ?: return false

        val responseText = app.post(
            ENDPOINT_VIDEO,
            headers = getWebHeaders(),
            json = mapOf("shortPlayId" to playId, "episodeNum" to epNum)
        ).text
        
        val videoObj = tryParseJson<VideoPlayResponse>(responseText)?.episode ?: return false
        val videoMap = videoObj.videoUrl ?: return false

        videoMap.forEach { (qualityKey, streamUrl) ->
            if (!streamUrl.isNullOrBlank()) {
                val mappedQuality = when (qualityKey) {
                    "video_1080" -> Qualities.P1080.value
                    "video_720"  -> Qualities.P720.value
                    "video_480"  -> Qualities.P480.value
                    else         -> Qualities.Unknown.value
                }

                val cleanLabel = qualityKey.replace("video_", "") + "p"

                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "ShortMax - $cleanLabel",
                        url = streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.quality = mappedQuality
                        this.referer = "$mainUrl/"
                    }
                )
            }
        }
        return true
    }

    // --- STRUKTUR MODEL DATA KELAS ---
    data class ShortPlayListResponse(
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("results") val results: List<ShortPlayItem>? = null,
        @JsonProperty("data") val data: NestedListDataHub? = null
    )

    data class NestedListDataHub(
        @JsonProperty("results") val results: List<ShortPlayItem>? = null,
        @JsonProperty("list") val list: List<ShortPlayItem>? = null,
        @JsonProperty("isEnd") val isEnd: Boolean? = null
    )

    data class ShortPlayItem(
        @JsonProperty("shortPlayId") val shortPlayId: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("cover") val cover: String? = null
    )

    data class ShortPlayDetailResponse(
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("data") val data: DetailDataHub? = null
    )
    
    data class ShortPlayDetailAltResponse(
        @JsonProperty("code") val code: Int? = null,
        @JsonProperty("data") val data: DetailDataHub? = null
    )

    data class DetailDataHub(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("shortPlayName") val shortPlayName: String? = null,
        @JsonProperty("summary") val summary: String? = null,
        @JsonProperty("totalEpisodes") val totalEpisodes: Int? = null,
        @JsonProperty("picUrl") val picUrl: String? = null,
        @JsonProperty("labelResponseList") val labelResponseList: List<LabelItem>? = null
    )

    data class LabelItem(
        @JsonProperty("labelName") val labelName: String? = null
    )

    data class VideoPlayResponse(
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("episode") val episode: EpisodeStreamContainer? = null
    )

    data class EpisodeStreamContainer(
        @JsonProperty("videoUrl") val videoUrl: Map<String, String>? = null
    )

    data class EpisodePayload(
        @JsonProperty("playId") val playId: Int? = null,
        @JsonProperty("episodeNum") val episodeNum: Int? = null
    ) {
        fun toJsonString(): String = this.toJson()
    }
}
