package data.provider.providers

import data.provider.*
import io.ktor.client.request.*

class Kakuyomu : WebNovelProvider {
    companion object {
        const val id = "kakuyomu"
    }

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val url = "https://kakuyomu.jp/works/$novelId"
        val doc = client.get(url).document()

        val title = doc
            .selectFirst("h1#workTitle > a")!!
            .text()

        val author = doc
            .selectFirst("span#workAuthor-activityName > a")!!
            .selectFirst("a")!!
            .let {
                RemoteNovelMetadata.Author(
                    name = it.text(),
                    link = "https://kakuyomu.jp" + it.attr("href"),
                )
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
                RemoteNovelMetadata.TocItem(
                    title = it.selectFirst("span")!!.text(),
                    chapterId = it.selectFirst("a")?.attr("href")?.substringAfterLast("/")
                )
            }

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url = "https://kakuyomu.jp/works/$novelId/episodes/$chapterId"
        val doc = client.get(url).document()
        return RemoteChapter(paragraphs = doc.select("div.widget-episodeBody > p").map { it.text() })
    }
}