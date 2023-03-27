package data.provider.providers

import data.provider.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

class Alphapolis : BookProvider {
    companion object {
        const val id = "alphapolis"
    }

    init {
        runBlocking {
            cookies.addCookie(
                "https://www.alphapolis.co.jp",
                Cookie(name = "_pubcid", value = "8807a80b-fc00-4c56-b151-95e6780d9f8f", domain = ".alphapolis.co.jp")
            )
        }
    }

    override suspend fun getRank(options: Map<String, String>): List<SBookListItem> {
        TODO("Not yet implemented")
    }

    private fun getMetadataUrl(bookId: String): String {
        return "https://www.alphapolis.co.jp/novel/" + bookId.split('-').joinToString("/")
    }

    private fun getEpisodeUrl(bookId: String, episodeId: String): String {
        return getMetadataUrl(bookId) + "/episode/" + episodeId
    }

    override suspend fun getMetadata(bookId: String): SBookMetadata {
        val doc = clientText.get(getMetadataUrl(bookId)).document()
        val main = doc.selectFirst("div.content-main")!!
        val title = main.selectFirst("h1.title")!!.text()
        val author = main
            .selectFirst("div.author")!!
            .selectFirst("a")!!
            .let {
                SBookAuthor(
                    name = it.text(),
                    link = "https://www.alphapolis.co.jp" + it.attr("href"),
                )
            }
        val introduction = main.selectFirst("div.abstract")!!.text()

        val toc = mutableListOf<SBookTocItem>()
        doc.selectFirst("div.episodes")!!.children().forEach { el ->
            if (el.hasClass("chapter-rental")) {
                toc.add(SBookTocItem(title = el.selectFirst("h3")!!.text()))
            } else if (el.hasClass("rental")) {
                el.select("div.rental-episode > a").not("[class]").forEach {
                    toc.add(
                        SBookTocItem(
                            title = it.text(),
                            episodeId = it.attr("href").substringAfterLast("/"),
                        )
                    )
                }
            } else if (el.tagName() == "h3") {
                toc.add(SBookTocItem(title = el.text()))
            } else if (el.hasClass("episode")) {
                toc.add(
                    SBookTocItem(
                        title = el.selectFirst("span.title")!!.text(),
                        episodeId = el.selectFirst("a")!!.attr("href").substringAfterLast("/"),
                    )
                )
            }
        }

        return SBookMetadata(
            title = title,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode {
        val doc = clientText.get(getEpisodeUrl(bookId, episodeId)).document()
        val paragraphs = doc.selectFirst("div#novelBoby")!!.textNodes()
            .map { it.text().removePrefix(" ") }
        return SBookEpisode(paragraphs = paragraphs)
    }
}
