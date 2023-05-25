package infra.provider.providers

import infra.provider.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

class Novelup : WebNovelProvider {
    companion object {
        const val id = "novelup"
    }

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val url = "https://novelup.plus/story/$novelId"
        val doc = client.get(url).document()

        val title = doc.selectFirst("div.novel_title > h1")!!.text()

        val author = doc
            .selectFirst("div.novel_author > p > a")!!
            .let {
                RemoteNovelMetadata.Author(
                    name = it.text(),
                    link = it.attr("href"),
                )
            }

        val introduction = doc.selectFirst("div.novel_synopsis")!!.text()

        val totalPage = doc.selectFirst("ul.pagination")!!.children().last()!!.let {
            it.selectFirst("a")
                ?.attr("href")
                ?.substringAfterLast("=")
                ?.toInt()
                ?: 1
        }

        val toc = (1..totalPage).asFlow()
            .map { page ->
                if (page == 1) doc
                else client.get("https://novelup.plus/story/$novelId?p=$page").document()
            }.map { subdoc ->
                subdoc.selectFirst("div.episode_list")!!.select("li").map { li ->
                    li.selectFirst("a")?.let {
                        RemoteNovelMetadata.TocItem(
                            title = it.text(),
                            chapterId = it.attr("href").substringAfterLast("/")
                        )
                    } ?: RemoteNovelMetadata.TocItem(
                        title = li.text(),
                    )
                }
            }.toList().flatten()

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url = "https://novelup.plus/story/$novelId/$chapterId"
        val doc = client.get(url).document()
        return RemoteChapter(paragraphs = doc.selectFirst("p#episode_content")!!.wholeText().lines())
    }
}