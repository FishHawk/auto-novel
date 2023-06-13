package infra.provider.providers

import infra.provider.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class Hameln : WebNovelProvider {
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

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val url = "https://syosetu.org/novel/$novelId"
        val doc = client.get(url).document()

        if (doc.selectFirst("span[itemprop=name]") != null) {
            val title = doc.selectFirst("span[itemprop=name]")!!.text()

            val authorTag = doc.selectFirst("span[itemprop=author]")!!
            val author = authorTag.selectFirst("a")
                ?.let {
                    RemoteNovelMetadata.Author(
                        name = it.text(),
                        link = "https:" + it.attr("href"),
                    )
                }
                ?: RemoteNovelMetadata.Author(
                    name = authorTag.text(),
                )

            val introduction = doc.select("div.ss")[1].wholeText().trimEnd()

            val toc = doc.select("tbody > tr").map { trTag ->
                trTag.selectFirst("a")?.let {
                    val chapterId = it.attr("href").removePrefix("./").removeSuffix(".html")
                    RemoteNovelMetadata.TocItem(
                        title = it.text(),
                        chapterId = chapterId,
                        createAt = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.JAPAN).parse(
                            trTag.selectFirst("nobr")!!.childNode(0)
                                .let { if (it is Element) it.text() else it.toString() }
                                .replace("\\(.*?\\)".toRegex(), "")

                        ).toInstant().toKotlinInstant(),
                    )
                } ?: RemoteNovelMetadata.TocItem(
                    title = trTag.text(),
                )
            }

            return RemoteNovelMetadata(
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
                    RemoteNovelMetadata.Author(
                        name = it.text(),
                        link = "https:" + it.attr("href"),
                    )
                } ?: RemoteNovelMetadata.Author(
                    name = pTag.text().substringAfter(" 　 作："),
                )
                Pair(title, author)
            }
            val introduction = ssList[1].text()

            return RemoteNovelMetadata(
                title = title,
                authors = listOf(author),
                introduction = introduction,
                toc = listOf(
                    RemoteNovelMetadata.TocItem(
                        title = "无名",
                        chapterId = "default",
                    )
                ),
            )
        }
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url =
            if (chapterId == "default") "https://syosetu.org/novel/$novelId"
            else "https://syosetu.org/novel/$novelId/$chapterId.html"
        val doc = client.get(url).document()
        return RemoteChapter(paragraphs = doc.select("div#honbun > p").map { it.text() })
    }
}