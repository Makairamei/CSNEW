package com.yunshanid

import com.lagradost.cloudstream3.*
import org.jsoup.nodes.Document

object YunshanParser {

    // ================= HOME =================
    fun parseHome(doc: Document, name: String): HomePageResponse {

        val items = doc.select("article, .item, .grid-item").mapNotNull { el ->

            val a = el.selectFirst("a") ?: return@mapNotNull null

            val title = a.attr("title").ifBlank { a.text() }
            val url = fixUrl(a.attr("href"))
            val poster = fixUrlNull(el.selectFirst("img")?.attr("src"))

            newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(name, items)
    }

    // ================= SEARCH =================
    fun parseSearch(doc: Document): List<SearchResponse> {

        return doc.select("article, .item, .grid-item").mapNotNull { el ->

            val a = el.selectFirst("a") ?: return@mapNotNull null

            val title = a.attr("title").ifBlank { a.text() }
            val url = fixUrl(a.attr("href"))

            newAnimeSearchResponse(title, url, TvType.Anime)
        }
    }

    // ================= DETAIL =================
    fun parseDetail(doc: Document, url: String): LoadResponse? {

        val title = doc.selectFirst("h1")?.text() ?: return null
        val poster = doc.selectFirst("img")?.attr("src")

        val episodes = doc.select("a[href*='episode'], .episode a").mapNotNull {

            val epUrl = fixUrl(it.attr("href"))
            val epName = it.text()

            newEpisode(epUrl) {
                this.name = epName
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.episodes = episodes
        }
    }
}