package infra.provider.providers

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

        val title = doc
            .selectFirst("h1#workTitle > a")!!
            .text()

        val author = doc
            .selectFirst("span#workAuthor-activityName > a")!!
            .selectFirst("a")!!
            .let {
                RemoteNovelMetadata.Author(
                    name = it.text(),
                    link = "https://kakuyomu.jp" + it.attr("href"),
                )
            }

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