package infra.provider.providers

import infra.model.WebNovelAttention
import infra.model.WebNovelAuthor
import infra.model.WebNovelType
import infra.provider.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
import org.jsoup.nodes.Element
import java.lang.RuntimeException
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
        val (doc1, doc2) = coroutineScope {
            val url1 = "https://syosetu.org/novel/$novelId"
            val url2 = "https://syosetu.org/?mode=ss_detail&nid=$novelId"
            return@coroutineScope listOf(
                async { client.get(url1).document() },
                async { client.get(url2).document() },
            ).awaitAll()
        }

        fun qTable(str: String) = "td:matches(^$str\$)"
        val title = doc2
            .selectFirst(qTable("タイトル"))!!
            .nextElementSibling()!!
            .text()

        val author = doc2
            .selectFirst(qTable("作者"))!!
            .nextElementSibling()!!
            .let { el ->
                WebNovelAuthor(
                    name = el.text(),
                    link = el.selectFirst("a")?.attr("href")?.let { "https:$it" },
                )
            }

        val type = doc2
            .selectFirst(qTable("話数"))!!
            .nextElementSibling()!!
            .text()
            .let {
                when {
                    it.startsWith("連載(完結)") -> WebNovelType.已完结
                    it.startsWith("連載(未完)") -> WebNovelType.连载中
                    it.startsWith("連載(連載中)") -> WebNovelType.连载中
                    it.startsWith("短編") -> WebNovelType.短篇
                    else -> throw RuntimeException("无法解析的小说类型:$it")
                }
            }

        val attentions = mutableSetOf<WebNovelAttention>()
        val keywords = mutableListOf<String>()
        listOf(
            doc2.selectFirst(qTable("タグ")),
            doc2.selectFirst(qTable("必須タグ"))
        )
            .flatMap { it!!.nextElementSibling()!!.select("a") }
            .map { it.text() }
            .forEach {
                when (it) {
                    "残酷な描写" -> attentions.add(WebNovelAttention.残酷描写)
                    "R-15" -> attentions.add(WebNovelAttention.R15)
                    "R-18" -> attentions.add(WebNovelAttention.R18)
                    else -> keywords.add(it)
                }
            }
        val introduction = doc2
            .selectFirst(qTable("あらすじ"))!!
            .nextElementSibling()!!
            .text()

        val toc = if (doc1.selectFirst("span[itemprop=name]") != null) {
            doc1.select("tbody > tr").map { trTag ->
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
        } else {
            listOf(
                RemoteNovelMetadata.TocItem(
                    title = "无名",
                    chapterId = "default",
                )
            )
        }

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            type = type,
            attentions = attentions.toList(),
            keywords = keywords,
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url =
            if (chapterId == "default") "https://syosetu.org/novel/$novelId"
            else "https://syosetu.org/novel/$novelId/$chapterId.html"
        val doc = client.get(url).document()
        return RemoteChapter(paragraphs = doc.select("div#honbun > p").map { it.text() })
    }
}