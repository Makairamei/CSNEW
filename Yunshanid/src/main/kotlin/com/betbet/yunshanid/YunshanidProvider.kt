package com.betbet.yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YunshanidProvider : MainAPI() {

    override var mainUrl = "https://yunshanid.site"
    override var name = "Yunshanid"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/donghua/" to "Donghua",
        "$mainUrl/ongoing/" to "Ongoing",
        "$mainUrl/completed/" to "Completed"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = "${request.data}page/$page/"
        val document = app.get(url).document

        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            request.name,
            home
        )
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {

        val title = this.selectFirst("h2.entry-title")?.text()?.trim()
            ?: return null

        val href = this.selectFirst("a")?.attr("href")
            ?: return null

        val poster = this.selectFirst("img")?.attr("src")

        return newAnimeSearchResponse(
            title,
            href,
            TvType.Anime
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document

        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")
            ?.text()?.trim()
            ?: "No Title"

        val poster = document.selectFirst(".thumb img")
            ?.attr("src")

        val description = document.selectFirst(".entry-content p")
            ?.text()

        val episodes = document.select("div.eplister li").mapIndexed { index, ep ->

            val epName = ep.selectFirst("a")?.text()?.trim()

            val epHref = ep.selectFirst("a")
                ?.attr("href")
                ?: ""

            Episode(
                epHref,
                epName
            ).apply {
                episode = index + 1
            }
        }.reversed()

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {
            posterUrl = poster
            plot = description
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val iframe = document.selectFirst("iframe")
            ?.attr("src")
            ?: return false

        loadExtractor(
            iframe,
            data,
            subtitleCallback,
            callback
        )

        return true
    }
}