package com.betbet.yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YunshanidProvider : MainAPI() {

    override var mainUrl = "https://yunshanid.site"
    override var name = "Yunshanid"
    override val hasMainPage = true
    override var lang = "id"

    override var sequentialMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.Movie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/ongoing/" to "Ongoing",
        "$mainUrl/completed/" to "Completed",
        "$mainUrl/category/movie/" to "Movie"
    )

    // =========================
    // SAFE SELECTOR
    // =========================

    private fun Element.safeText(
        vararg queries: String
    ): String? {

        queries.forEach {

            val value =
                selectFirst(it)
                    ?.text()
                    ?.trim()

            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    private fun Element.safeAttr(
        attr: String,
        vararg queries: String
    ): String? {

        queries.forEach {

            val value =
                selectFirst(it)
                    ?.attr(attr)

            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    // =========================
    // SEARCH RESULT
    // =========================

    private fun Element.toSearchResult(): SearchResponse? {

        val title = safeText(
            "h2",
            ".tt",
            ".entry-title"
        ) ?: return null

        val href = safeAttr(
            "href",
            "a"
        ) ?: return null

        val poster = safeAttr(
            "data-src",
            "img"
        ) ?: safeAttr(
            "src",
            "img"
        )

        val type =
            if (title.contains("movie", true))
                TvType.Movie
            else
                TvType.Anime

        return newAnimeSearchResponse(
            title,
            href,
            type
        ) {
            this.posterUrl = poster
        }
    }

    // =========================
    // MAIN PAGE
    // =========================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url =
            if (page == 1)
                request.data
            else
                "${request.data}page/$page/"

        val document =
            app.get(url).document

        val home = document.select(
            "article, .bs, .bsx"
        ).mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            request.name,
            home.distinctBy {
                it.url
            }
        )
    }

    // =========================
    // SEARCH
    // =========================

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val document =
            app.get(
                "$mainUrl/?s=$query"
            ).document

        return document.select(
            "article, .bs, .bsx"
        ).mapNotNull {
            it.toSearchResult()
        }
    }

    // =========================
    // LOAD DETAIL
    // =========================

    override suspend fun load(
        url: String
    ): LoadResponse {

        val document =
            app.get(url).document

        val title = document.safeText(
            "h1.entry-title",
            ".entry-title",
            "h1"
        ) ?: "No Title"

        val poster = document.safeAttr(
            "src",
            ".thumb img",
            ".infox img",
            "img"
        )

        val description = document.safeText(
            ".entry-content p",
            ".desc",
            ".synp"
        )

        val tags = document.select(
            ".genxed a, .mgen a"
        ).map {
            it.text()
        }

        val recommendations =
            document.select(
                ".bs, .bsx, article"
            ).mapNotNull {
                it.toSearchResult()
            }

        val episodes =
            document.select(
                ".eplister li, #chapterlist li, .episodelist li"
            ).mapIndexed { index, ep ->

                val epName =
                    ep.selectFirst("a")
                        ?.text()
                        ?.trim()

                val epUrl =
                    ep.selectFirst("a")
                        ?.attr("href")
                        ?: ""

                newEpisode(epUrl) {
                    this.name = epName
                    this.episode = index + 1
                }
            }

        val finalEpisodes =
            if (episodes.isNotEmpty()) {
                episodes.reversed()
            } else {
                listOf(
                    newEpisode(url) {
                        this.name = "Full Movie"
                    }
                )
            }

        val type =
            if (title.contains("movie", true))
                TvType.Movie
            else
                TvType.Anime

        return newAnimeLoadResponse(
            title,
            url,
            type
        ) {

            posterUrl = poster

            plot = description

            this.tags = tags

            this.recommendations =
                recommendations

            addEpisodes(
                DubStatus.Subbed,
                finalEpisodes
            )
        }
    }

    // =========================
    // LOAD LINKS
    // =========================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document =
            app.get(
                data,
                referer = mainUrl
            ).document

        val links =
            mutableListOf<String>()

        // iframe
        document.select("iframe")
            .forEach {

                val src =
                    it.attr("src")

                if (src.isNotBlank()) {
                    links.add(src)
                }
            }

        // href
        document.select("a[href]")
            .forEach {

                val href =
                    it.attr("href")

                if (
                    href.contains("mp4upload", true) ||
                    href.contains("dood", true) ||
                    href.contains("stream", true) ||
                    href.contains("filemoon", true)
                ) {

                    links.add(href)
                }
            }

        links.distinct().forEach { link ->

            try {

                loadExtractor(
                    link,
                    data,
                    subtitleCallback,
                    callback
                )

            } catch (_: Exception) {
            }
        }

        return true
    }
}