package infra.provider.providers

import infra.model.WebNovelAttention
import infra.model.WebNovelAuthor
import infra.model.WebNovelType
import infra.provider.*
import io.ktor.client.request.*
import kotlinx.datetime.Instant

class Kakuyomu : WebNovelProvider {
    companion object {
        const val id = "kakuyomu"
        private val rangeIds = mapOf(
            "每日" to "daily",
            "每周" to "weekly",
            "每月" to "monthly",
            "每年" to "yearly",
            "总计" to "entire",
        )
        private val genreIds = mapOf(
            "综合" to "all",
            "异世界幻想" to "fantasy",
            "现代幻想" to "action",
            "科幻" to "sf",
            "恋爱" to "love_story",
            "浪漫喜剧" to "romance",
            "现代戏剧" to "drama",
            "恐怖" to "horror",
            "推理" to "mystery",
            "散文·纪实" to "nonfiction",
            "历史·时代·传奇" to "history",
            "创作论·评论" to "criticism",
            "诗·童话·其他" to "others",
        )
    }

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        val genre = genreIds[options["genre"]] ?: return emptyList()
        val range = rangeIds[options["range"]] ?: return emptyList()
        val url = "https://kakuyomu.jp/rankings/${genre}/${range}"
        val doc = client.get(url).document()
        return doc.select("div.widget-media-genresWorkList-right > div.widget-work").map { workCard ->
            val a = workCard.selectFirst("a.bookWalker-work-title")!!
            val novelId = a.attr("href").removePrefix("/works/")
            val title = a.text()
            val meta1 = workCard.selectFirst("p.widget-workCard-meta")!!.children()
                .joinToString("/") { it.text() }
            val meta2 = workCard.select("p.widget-workCard-summary > span")
                .joinToString("/") { it.text() }
            RemoteNovelListItem(novelId = novelId, title = title, meta = "$meta1\n$meta2")
        }
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val url = "https://kakuyomu.jp/works/$novelId"
        val doc = client.get(url).document()

        val infoEl = doc.selectFirst("section#work-information")!!

        val title = infoEl
            .selectFirst("header > h4 > a")!!
            .text()

        val author = infoEl
            .selectFirst("header > h5 > a")!!
            .let {
                WebNovelAuthor(
                    name = it.child(0).text(),
                    link = "https://kakuyomu.jp" + it.attr("href"),
                )
            }

        val type = doc
            .selectFirst("p.widget-toc-workStatus > span")!!
            .text()
            .let {
                when (it) {
                    "完結済" -> WebNovelType.已完结
                    "連載中" -> WebNovelType.连载中
                    else -> throw RuntimeException("无法解析的小说类型:$it")
                }
            }

        val attentions = doc
            .select("ul#workMeta-attention > li")
            .mapNotNull {
                when (it.text()) {
                    "残酷描写有り" -> WebNovelAttention.残酷描写
                    "暴力描写有り" -> WebNovelAttention.暴力描写
                    "性描写有り" -> WebNovelAttention.性描写
                    else -> null
                }
            }

        val keywords = doc
            .select("ul#workMeta-tags > li")
            .mapNotNull { it.text() }

        val introduction = doc
            .selectFirst("p#introduction")
            ?.wholeText()
            ?.trimEnd()
            ?.removeSuffix("…続きを読む")
            ?: ""

        val toc = doc
            .select("ol.widget-toc-items > li")
            .map {
                RemoteNovelMetadata.TocItem(
                    title = it.selectFirst("span")!!.text(),
                    chapterId = it.selectFirst("a")?.attr("href")?.substringAfterLast("/"),
                    createAt = it.selectFirst("time")?.attr("datetime")?.let { Instant.parse(it) },
                )
            }

        return RemoteNovelMetadata(
            title = title,
            authors = listOf(author),
            type = type,
            attentions = attentions,
            keywords = keywords,
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url = "https://kakuyomu.jp/works/$novelId/episodes/$chapterId"
        val doc = client.get(url).document()
        return RemoteChapter(paragraphs = doc.select("div.widget-episodeBody > p").map { it.text() })
    }
}