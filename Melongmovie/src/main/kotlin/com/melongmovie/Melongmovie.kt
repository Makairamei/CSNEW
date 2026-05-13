package com.melongmovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Melongmovie : MainAPI() {

    override var mainUrl = "http://139.59.189.160"
    override var name = "Melongmovie🪁"
    override var lang = "id"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/latest-movies/page/%d/" to "Movie Terbaru",
        "$mainUrl/advanced-search/page/%d/?order=latest&country[]=china&type[]=post" to "Movie China",
        "$mainUrl/advanced-search/page/%d/?order=latest&country[]=hong-kong&type[]=post" to "Movie Hongkong",
        "$mainUrl/advanced-search/page/%d/?order=latest&country[]=india&type[]=post" to "Movie India",
        "$mainUrl/advanced-search/page/%d/?order=latest&country[]=japan&type[]=post" to "Movie Jepang"
    )

    // ---------------- MAIN PAGE ----------------
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val safePage = if (page <= 0) 1 else page
        val url = request.data.format(safePage)

        val document = app.get(url).document

        val items = document.select("div.los article.box")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            HomePageList(request.name, items),
            hasNext = items.isNotEmpty()
        )
    }

    // ---------------- SEARCH ----------------
    override suspend fun search(query: String): List<SearchResponse> {

        val document = app.get("$mainUrl/?s=$query", timeout = 50L).document

        return document.select("div.los article.box")
            .mapNotNull { it.toSearchResult() }
    }

    // ---------------- SEARCH PARSER ----------------
    private fun Element.toSearchResult(): SearchResponse? {

        val link = this.selectFirst("a") ?: return null
        val href = fixUrl(link.attr("href"))

        val title = link.attr("title").ifBlank {
            this.selectFirst("h2.entry-title")?.text()
        } ?: return null

        val poster = this.selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
        val quality = this.selectFirst("span.quality")?.text()

        val isSeries = href.contains("/series/", true) || href.contains("season", true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.quality = getQualityFromString(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.quality = getQualityFromString(quality)
            }
        }
    }

    // ---------------- LOAD ----------------
    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url).document

        val title = doc.selectFirst("h1.entry-title")?.text().orEmpty()
        val poster = doc.selectFirst("div.limage img")?.getImageAttr()?.let { fixUrlNull(it) }
        val description = doc.selectFirst("div.bixbox > p")?.text()?.trim()

        val tags = doc.select("ul.data li:has(b:contains(Genre)) a").map { it.text() }
        val actors = doc.select("ul.data li:has(b:contains(Stars)) a").map { it.text() }

        val rating = doc.selectFirst("span.ratingValue, span[itemprop=ratingValue]")
            ?.text()?.toDoubleOrNull()

        val duration = doc.selectFirst("span[property=duration]")
            ?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()

        val recommendations = doc.select("div.latest.relat article.box")
            .mapNotNull { it.toRecommendResult() }

        val hasIframe = doc.select("iframe").isNotEmpty()

        return if (hasIframe) {

            val episodes = listOf(
                newEpisode(url) {
                    this.name = "Play"
                    this.episode = 1
                }
            )

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.duration = duration ?: 0
                this.recommendations = recommendations
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
            }

        } else {

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.duration = duration ?: 0
                this.recommendations = recommendations
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
            }
        }
    }

    // ---------------- LOAD LINKS (MINIMAL FIX) ----------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document
        var found = false

        doc.select("iframe, div#embed_holder iframe").forEach {

            val src = it.attr("src")

            if (src.isNotBlank()) {
                loadExtractor(src, data, subtitleCallback, callback)
                found = true
            }
        }

        return found
    }

    // ---------------- HELPERS ----------------
    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("data-lazy-src") -> this.attr("data-lazy-src")
            this.hasAttr("srcset") -> this.attr("srcset").substringBefore(" ")
            else -> this.attr("src")
        }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val img = this.selectFirst("img")
        val title = img?.attr("alt")?.trim() ?: return null

        val poster = fixUrlNull(img?.getImageAttr())

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }
}