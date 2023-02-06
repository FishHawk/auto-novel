package data.provider.providers

import data.provider.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

class Syosetu : BookProvider {
    companion object {
        const val id = "syosetu"
    }

    init {
        runBlocking {
            cookies.addCookie(
                "https://ncode.syosetu.com/",
                Cookie(name = "over18", value = "yes", domain = ".syosetu.com")
            )
        }
    }

    override fun getMetadataUrl(bookId: String): String {
        return "https://ncode.syosetu.com/$bookId"
    }

    override fun getEpisodeUrl(bookId: String, episodeId: String): String {
        return if (episodeId == "default") "https://ncode.syosetu.com/$bookId"
        else "https://ncode.syosetu.com/$bookId/$episodeId"
    }


    override suspend fun getMetadata(bookId: String): SBookMetadata {
        val doc = client.get(getMetadataUrl(bookId)).document()

        val title = doc.selectFirst("p.novel_title")!!.text()

        val author = doc.selectFirst("div.novel_writername")!!.let { el ->
            el.selectFirst("a")?.let {
                SBookAuthor(name = it.text(), link = it.attr("href"))
            } ?: SBookAuthor(name = el.text(), link = null)
        }

        if (doc.selectFirst("div.index_box") == null) {
            return SBookMetadata(
                title = title,
                authors = listOf(author),
                introduction = "",
                toc = listOf(SBookTocItem(title = "无名", episodeId = "default")),
            )
        } else {
            val introduction = doc.selectFirst("div#novel_ex")!!.wholeText()

            val toc = doc
                .selectFirst("div.index_box")!!
                .children()
                .map { child ->
                    child.selectFirst("a")?.let { a ->
                        SBookTocItem(
                            title = a.text(),
                            episodeId = a.attr("href")
                                .removeSuffix("/")
                                .substringAfterLast("/")
                        )
                    } ?: SBookTocItem(title = child.text())
                }

            return SBookMetadata(
                title = title,
                authors = listOf(author),
                introduction = introduction,
                toc = toc,
            )
        }
    }

    override suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode {
        val doc = client.get(getEpisodeUrl(bookId, episodeId)).document()
        return SBookEpisode(paragraphs = doc.select("div#novel_honbun > p").map { it.text() })
    }
}