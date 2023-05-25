package infra.provider.providers

import infra.provider.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

class Alphapolis : WebNovelProvider {
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

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        TODO("Not yet implemented")
    }

    private fun getMetadataUrl(novelId: String): String {
        return "https://www.alphapolis.co.jp/novel/" + novelId.split('-').joinToString("/")
    }

    private fun getEpisodeUrl(novelId: String, chapterId: String): String {
        return getMetadataUrl(novelId) + "/episode/" + chapterId
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val doc = clientText.get(getMetadataUrl(novelId)).document()
        val main = doc.selectFirst("div.content-main")!!
        val title = main.selectFirst("h1.title")!!.text()
        val author = main
            .selectFirst("div.author")!!
            .selectFirst("a")!!
            .let {
                RemoteNovelMetadata.Author(
                    name = it.text(),
                    link = "https://www.alphapolis.co.jp" + it.attr("href"),
                )
            }
        val introduction = main.selectFirst("div.abstract")!!.text()

        val toc = mutableListOf<RemoteNovelMetadata.TocItem>()
        doc.selectFirst("div.episodes")!!.children().forEach { el ->
            if (el.hasClass("chapter-rental")) {
                toc.add(
                    RemoteNovelMetadata.TocItem(
                        title = el.selectFirst("h3")!!.text(),
                    )
                )
            } else if (el.hasClass("rental")) {
                el.select("div.rental-episode > a").not("[class]").forEach {
                    toc.add(
                        RemoteNovelMetadata.TocItem(
                            title = it.text(),
                            chapterId = it.attr("href").substringAfterLast("/"),
                        )
                    )
                }
            } else if (el.tagName() == "h3") {
                toc.add(
                    RemoteNovelMetadata.TocItem(
                        title = el.text(),
                    )
                )
            } else if (el.hasClass("episode")) {
                toc.add(
                    RemoteNovelMetadata.TocItem(
                        title = el.selectFirst("span.title")!!.text(),
                        chapterId = el.selectFirst("a")!!.attr("href").substringAfterLast("/"),
                    )
                )
            }
        }

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val doc = clientText.get(getEpisodeUrl(novelId, chapterId)).document()
        val paragraphs = doc.selectFirst("div#novelBoby")!!.textNodes()
            .map { it.text().removePrefix(" ") }
        if (paragraphs.size < 5) {
            throw RuntimeException("章节内容太少，爬取频率太快导致未加载")
        }
        return RemoteChapter(paragraphs = paragraphs)
    }
}
