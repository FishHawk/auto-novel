package data.provider.providers

import data.provider.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

class Hameln : BookProvider {
    companion object {
        const val id = "hameln"
    }

    init {
        runBlocking {
            cookies.addCookie(
                "https://syosetu.org/",
                Cookie(name = "over18", value = "off", domain = ".syosetu.org")
            )
        }
    }

    override fun getMetadataUrl(bookId: String): String {
        return "https://syosetu.org/novel/$bookId"
    }

    override fun getEpisodeUrl(bookId: String, episodeId: String): String {
        return if (episodeId == "default") "https://syosetu.org/novel/$bookId"
        else "https://syosetu.org/novel/$bookId/$episodeId.html"
    }

    override suspend fun getMetadata(bookId: String): SBookMetadata {
        val doc = client.get(getMetadataUrl(bookId)).document()

        if (doc.selectFirst("span[itemprop=name]") != null) {
            val title = doc.selectFirst("span[itemprop=name]")!!.text()

            val authorTag = doc.selectFirst("span[itemprop=author]")!!
            val author = authorTag.selectFirst("a")
                ?.let { SBookAuthor(name = it.text(), link = "https:" + it.attr("href")) }
                ?: SBookAuthor(name = authorTag.text())

            val introduction = doc.select("div.ss")[1].wholeText().trimEnd()

            val toc = doc.select("tbody > tr").map { trTag ->
                trTag.selectFirst("a")?.let {
                    val episodeId = it.attr("href").removePrefix("./").removeSuffix(".html")
                    SBookTocItem(title = it.text(), episodeId = episodeId)
                } ?: SBookTocItem(title = trTag.text())
            }

            return SBookMetadata(
                title = title,
                authors = listOf(author),
                introduction = introduction,
                toc = toc,
            )
        } else {
            val ssList = doc.select("div.ss")
            val (title, author) = ssList[0].selectFirst("div.ss > p")!!.let { pTag ->
                val aList = pTag.select("a")
                val title = aList[0].text()
                val author = aList.getOrNull(1)?.let {
                    SBookAuthor(name = it.text(), link = "https:" + it.attr("href"))
                } ?: SBookAuthor(name = pTag.text().substringAfter(" 　 作："))
                Pair(title, author)
            }
            val introduction = ssList[1].text()

            return SBookMetadata(
                title = title,
                authors = listOf(author),
                introduction = introduction,
                toc = listOf(SBookTocItem(title = "无名", episodeId = "default")),
            )
        }
    }

    override suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode {
        val doc = client.get(getEpisodeUrl(bookId, episodeId)).document()
        return SBookEpisode(paragraphs = doc.select("div#honbun > p").map { it.text() })
    }
}