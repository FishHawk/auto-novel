package data.provider.providers

import data.provider.*
import io.ktor.client.request.*

class Kakuyomu : BookProvider {
    companion object {
        const val id = "kakuyomu"
    }

    override suspend fun getRank(options: Map<String, String>): List<SBookListItem> {
        TODO("Not yet implemented")
    }

    private fun getMetadataUrl(bookId: String): String {
        return "https://kakuyomu.jp/works/$bookId"
    }

    private fun getEpisodeUrl(bookId: String, episodeId: String): String {
        return "https://kakuyomu.jp/works/$bookId/episodes/$episodeId"
    }

    override suspend fun getMetadata(bookId: String): SBookMetadata {
        val doc = client.get(getMetadataUrl(bookId)).document()

        val title = doc
            .selectFirst("h1#workTitle > a")!!
            .text()

        val author = doc
            .selectFirst("span#workAuthor-activityName > a")!!
            .selectFirst("a")!!
            .let {
                SBookAuthor(name = it.text(), link = "https://kakuyomu.jp" + it.attr("href"))
            }

        val introduction = doc
            .selectFirst("p#introduction")
            ?.wholeText()
            ?.trimEnd()
            ?.removeSuffix("…続きを読む")
            ?: ""

        val toc = doc
            .select("ol.widget-toc-items > li")
            .map {
                SBookTocItem(
                    title = it.selectFirst("span")!!.text(),
                    episodeId = it.selectFirst("a")?.attr("href")?.substringAfterLast("/")
                )
            }

        return SBookMetadata(
            title = title,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode {
        val doc = client.get(getEpisodeUrl(bookId, episodeId)).document()
        return SBookEpisode(paragraphs = doc.select("div.widget-episodeBody > p").map { it.text() })
    }
}