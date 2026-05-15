package com.Cam4

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class Cam4Provider : MainAPI() {
    override var mainUrl              = "https://www.cam4.com"
    override var name                 = "Cam4"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = false
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    private val commonHeaders = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "/api/directoryCams?directoryJson=true&online=true&url=true&orderBy=VIDEO_QUALITY&resultsPerPage=60" to "All",
        "/api/directoryCams?directoryJson=true&online=true&url=true&orderBy=VIDEO_QUALITY&gender=female&resultsPerPage=60" to "Female",
        "/api/directoryCams?directoryJson=true&online=true&url=true&orderBy=VIDEO_QUALITY&gender=male&resultsPerPage=60" to "Male",
        "/api/directoryCams?directoryJson=true&online=true&url=true&orderBy=VIDEO_QUALITY&gender=shemale&resultsPerPage=60" to "Shemale",
        "/api/directoryCams?directoryJson=true&online=true&url=true&orderBy=VIDEO_QUALITY&gender=couple&resultsPerPage=60" to "Couples"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl${request.data}"
        val res = app.get(url, headers = commonHeaders).text
        
        val home = try {
            val json = parseJson<Response>(res)
            json.users.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            emptyList()
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun User.toSearchResult(): SearchResponse? {
        // Memastikan username tidak null untuk menghindari crash di parameter URL
        val name = this.username ?: return null
        val targetUrl = "$mainUrl/$name"
        
        return newAnimeSearchResponse(name, targetUrl, TvType.NSFW) {
            this.posterUrl = snapshotImageLink?.let { 
                if (it.startsWith("//")) "https:$it" else it 
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/directoryCams?directoryJson=true&online=true&url=true&search=$query"
        val res = app.get(url, headers = commonHeaders).text
        return try {
            val json = parseJson<Response>(res)
            json.users.mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = commonHeaders).document
        
        val title = (document.selectFirst("h1.profile-name") ?: document.selectFirst("title"))
            ?.text()?.trim() ?: "Cam4 User"
            
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst(".profile-avatar img")?.attr("src")

        val description = document.selectFirst("meta[property='og:description']")?.attr("content")

        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url,
        ).apply {
            this.posterUrl = fixUrlNull(poster)
            this.plot      = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val username = Uri.parse(data).path?.replace("/", "") ?: return false
        val streamUrl = "https://www.cam4.com/rest/v1.0/profile/$username/streamInfo"
        
        val res = app.get(streamUrl, headers = commonHeaders).text
        if (res.isBlank()) return false
        
        val json = JSONObject(res)
        // Cek apakah model sedang live (punya cdnURL)
        if (!json.has("cdnURL")) return false
        
        val m3u8 = json.optString("cdnURL")
        if (m3u8.isNullOrBlank()) return false

        callback.invoke(
            newExtractorLink(
                source = name,
                name   = name,
                url    = m3u8,
                referer = "$mainUrl/",
                type   = ExtractorLinkType.M3U8
            )
        )
        return true
    }

    data class User(
        @JsonProperty("username") val username: String? = null,
        @JsonProperty("snapshotImageLink") val snapshotImageLink: String? = null,
        @JsonProperty("userId") val userId: String? = null,
    )

    data class Response(
        @JsonProperty("users") val users: List<User> = arrayListOf()
    )
}
