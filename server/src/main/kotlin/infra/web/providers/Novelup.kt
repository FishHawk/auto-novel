package infra.web.providers

import domain.entity.*
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

class Novelup(
    private val client: HttpClient,
) : WebNovelProvider {
    companion object {
        const val id = "novelup"
    }

    override suspend fun getRank(options: Map<String, String>): Page<RemoteNovelListItem> {
        return emptyPage()
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val url = "https://novelup.plus/story/$novelId"
        val doc = client.get(url).document()

        val infoEl = doc.selectFirst("div#section_episode_info_table > div.content_wrapper > table")!!

        fun row(label: String) = infoEl
            .selectFirst("th:containsOwn(${label})")!!
            .nextElementSibling()!!

        val title = row("作品名")
            .text()

        val author = row("作者名")
            .selectFirst("a")!!
            .let {
                WebNovelAuthor(
                    name = it.text(),
                    link = it.attr("href"),
                )
            }

        val type = doc
            .selectFirst("div.state_lamp_set")!!
            .select("span")
            .last()!!
            .text()
            .let {
                when (it) {
                    "連載中" -> WebNovelType.连载中
                    "完結済" -> WebNovelType.已完结
                    else -> throw RuntimeException("无法解析的小说类型:$it")
                }
            }

        val attentions = row("セルフレイティング")
            .textNodes()
            .mapNotNull {
                when (it.text().trim()) {
                    "残酷描写あり" -> WebNovelAttention.残酷描写
                    "暴力描写あり" -> WebNovelAttention.暴力描写
                    "性的表現あり" -> WebNovelAttention.性描写
                    else -> null
                }
            }

        val keywords = row("タグ")
            .children()
            .map { it.text() }

        val points = row("応援ポイント")
            .text()
            .filter { it.isDigit() }
            .toInt()

        val totalCharacters = row("文字数")
            .text()
            .filter { it.isDigit() }
            .toInt()


        val introduction = doc
            .selectFirst("div.novel_synopsis")!!
            .text()

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
                            chapterId = it.attr("href").substringAfterLast("/"),
                            createAt = parseJapanDateString(
                                "yyyy/M/dd HH:mm",
                                li.selectFirst("p.episodeMeta_columnItem")!!.child(1).text()
                            ),
                        )
                    } ?: RemoteNovelMetadata.TocItem(
                        title = li.text(),
                    )
                }
            }.toList().flatten()

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            type = type,
            attentions = attentions,
            keywords = keywords,
            points = points,
            totalCharacters = totalCharacters,
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url = "https://novelup.plus/story/$novelId/$chapterId"
        val paragraphs = client.get(url).document()
            .selectFirst("p#episode_content")!!
            .apply {
                select("rp").remove()
                select("rt").remove()
            }
            .wholeText()
            .lines()
        return RemoteChapter(paragraphs = paragraphs)
    }
}