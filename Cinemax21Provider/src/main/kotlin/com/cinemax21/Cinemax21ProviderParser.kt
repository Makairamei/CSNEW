data class Moviebox2SearchResult(
    @param:JsonProperty("subjects") val subjects: ArrayList<Moviebox2Subject>? = arrayListOf()
)

data class Moviebox2Subject(
    @param:JsonProperty("subjectId") val subjectId: String? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("releaseDate") val releaseDate: String? = null,
    @param:JsonProperty("subjectType") val subjectType: Int? = null
)

data class Moviebox2PlayResponse(
    @param:JsonProperty("data") val data: Moviebox2PlayData? = null
)

data class Moviebox2PlayData(
    @param:JsonProperty("streams") val streams: ArrayList<Moviebox2Stream>? = arrayListOf()
)

data class Moviebox2Stream(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("format") val format: String? = null,
    @param:JsonProperty("resolutions") val resolutions: String? = null,
    @param:JsonProperty("signCookie") val signCookie: String? = null
)

data class Moviebox2SubtitleResponse(
    @param:JsonProperty("data") val data: Moviebox2SubtitleData? = null
)

data class Moviebox2SubtitleData(
    @param:JsonProperty("extCaptions") val extCaptions: ArrayList<Moviebox2Caption>? = arrayListOf()
)

data class Moviebox2Caption(
    @param:JsonProperty("url") val url: String? = null,
    @param:JsonProperty("language") val language: String? = null
)
