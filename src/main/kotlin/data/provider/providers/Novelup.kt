package data.provider.providers

import data.provider.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

class Novelup : BookProvider {
    companion object {
        const val id = "novelup"
    }

    override fun getMetadataUrl(bookId: String): String {
        return "https://novelup.plus/story/$bookId"
    }

    override fun getEpisodeUrl(bookId: String, episodeId: String): String {
        return "https://novelup.plus/story/$bookId/$episodeId"
    }

    override suspend fun getMetadata(bookId: String): SBookMetadata {
        val doc = client.get(getMetadataUrl(bookId)).document()

        val title = doc.selectFirst("div.novel_title > h1")!!.text()

        val author = doc
            .selectFirst("div.novel_author > p > a")!!
            .let { SBookAuthor(name = it.text(), link = it.attr("href")) }

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
                else client.get("https://novelup.plus/story/$bookId?p=$page").document()
            }.map { subdoc ->
                subdoc.selectFirst("div.episode_list")!!.select("li").map { li ->
                    li.selectFirst("a")?.let {
                        SBookTocItem(
                            title = it.text(),
                            episodeId = it.attr("href").substringAfterLast("/")
                        )
                    } ?: SBookTocItem(title = li.text())
                }
            }.toList().flatten()

        return SBookMetadata(
            title = title,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode {
        val doc = client.get(getEpisodeUrl(bookId, episodeId)).document()
        return SBookEpisode(paragraphs = doc.selectFirst("p#episode_content")!!.wholeText().lines())
    }
}