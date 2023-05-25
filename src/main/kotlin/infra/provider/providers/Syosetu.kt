package infra.provider.providers

import infra.provider.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

class Syosetu : WebNovelProvider {
    companion object {
        const val id = "syosetu"
        val rangeIds = mapOf(
            "每日" to "daily",
            "每周" to "weekly",
            "每月" to "monthly",
            "季度" to "quarter",
            "每年" to "yearly",
            "总计" to "total",
        )
        val genreIdsV1 = mapOf(
            "恋爱：异世界" to "101",
            "恋爱：现实世界" to "102",
            "幻想：高幻想" to "201",
            "幻想：低幻想" to "202",
            "文学：纯文学" to "301",
            "文学：人性剧" to "302",
            "文学：历史" to "303",
            "文学：推理" to "304",
            "文学：恐怖" to "305",
            "文学：动作" to "306",
            "文学：喜剧" to "307",
            "科幻：VR游戏" to "401",
            "科幻：宇宙" to "402",
            "科幻：空想科学" to "403",
            "科幻：惊悚" to "404",
            "其他：童话" to "9901",
            "其他：诗" to "9902",
            "其他：散文" to "9903",
            "其他：其他" to "9999",
        )
        val genreIdsV2 = mapOf(
            "全部" to "total",
            "短篇" to "t",
            "连载" to "r",
            "完结" to "er",
        )
        val genreIdsV3 = mapOf(
            "恋爱" to "1",
            "幻想" to "2",
            "文学/科幻/其他" to "o",
        )
    }

    init {
        runBlocking {
            cookies.addCookie(
                "https://ncode.syosetu.com/",
                Cookie(name = "over18", value = "yes", domain = ".syosetu.com")
            )
        }
    }

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        val genre = options["genre"] ?: return emptyList()
        val range = options["range"] ?: return emptyList()

        val rangeId = rangeIds[range] ?: return emptyList()
        val path = when (options["type"]) {
            "流派" -> {
                val genreId = genreIdsV1[genre] ?: return emptyList()
                "genrelist/type/${rangeId}_${genreId}"
            }

            "综合" -> {
                val genreId = genreIdsV2[genre] ?: return emptyList()
                "list/type/${rangeId}_${genreId}"
            }

            "异世界转生/转移" -> {
                val genreId = genreIdsV3[genre] ?: return emptyList()
                "isekailist/type/${rangeId}_${genreId}"
            }

            else -> return emptyList()
        }

        val doc = client.get("https://yomou.syosetu.com/rank/$path").document()
        return doc.select("div.ranking_inbox > div.ranking_list").map { parseListItem(it) }
    }

    private fun parseListItem(item: Element): RemoteNovelListItem {
        val novelId = item.selectFirst("a.tl")!!
            .attr("href")
            .removeSuffix("/")
            .substringAfterLast("/")

        val title = item.selectFirst("a.tl")!!.text()

        val meta = item.selectFirst("tbody")!!.let {
            val left = it.selectFirst("td.left")!!.text()
            val genre = it.child(2).text()
            val keywords = it.select("td.keyword > a").map { it.text() }
            listOf(
                (left.split(" ", limit = 2) + listOf(genre)).joinToString("/"),
                keywords.joinToString("/"),
            ).joinToString("\n")
        }

        return RemoteNovelListItem(novelId = novelId, title = title, meta = meta)
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val url = "https://ncode.syosetu.com/$novelId"
        val doc = client.get(url).document()

        val title = doc.selectFirst("p.novel_title")!!.text()

        val author = doc.selectFirst("div.novel_writername")!!.let { el ->
            el.selectFirst("a")?.let {
                RemoteNovelMetadata.Author(
                    name = it.text(),
                    link = it.attr("href"),
                )
            } ?: RemoteNovelMetadata.Author(
                name = el.text().removePrefix("作者："),
            )
        }

        if (doc.selectFirst("div.index_box") == null) {
            return RemoteNovelMetadata(
                title = title,
                authors = listOf(author),
                introduction = "",
                toc = listOf(
                    RemoteNovelMetadata.TocItem(
                        title = "无名",
                        chapterId = "default",
                    )
                ),
            )
        } else {
            val introduction = doc.selectFirst("div#novel_ex")!!.wholeText()

            val toc = doc
                .selectFirst("div.index_box")!!
                .children()
                .map { child ->
                    child.selectFirst("a")?.let { a ->
                        RemoteNovelMetadata.TocItem(
                            title = a.text(),
                            chapterId = a.attr("href")
                                .removeSuffix("/")
                                .substringAfterLast("/"),
                        )
                    } ?: RemoteNovelMetadata.TocItem(
                        title = child.text(),
                    )
                }

            return RemoteNovelMetadata(
                title = title,
                authors = listOf(author),
                introduction = introduction,
                toc = toc,
            )
        }
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url =
            if (chapterId == "default") "https://ncode.syosetu.com/$novelId"
            else "https://ncode.syosetu.com/$novelId/$chapterId"
        val doc = client.get(url).document()
        return RemoteChapter(paragraphs = doc.select("div#novel_honbun > p").map { it.text() })
    }
}