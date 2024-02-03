package infra.web.providers

import infra.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.serialization.json.*

class Novelism(
    private val client: HttpClient,
) : WebNovelProvider {
    companion object {
        const val id = "novelism"
    }

    override suspend fun getRank(options: Map<String, String>): Page<RemoteNovelListItem> {
        return emptyPage()
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val doc = client.get("https://novelism.jp/novel/$novelId").document()

        val title = doc
            .selectFirst("h1.mb-2 > span")!!
            .text()

        val author = doc
            .selectFirst("div.mb-1 > a.text-sm")!!
            .let {
                WebNovelAuthor(
                    name = it.text(),
                    link = "https://novelism.jp" + it.attr("href"),
                )
            }

        val type = doc
            .selectFirst("div.text-sm")!!
            .children()
            .map { it.text() }
            .let {
                when {
                    it.contains("短編") -> WebNovelType.短篇
                    it.contains("完結済") -> WebNovelType.已完结
                    it.contains("連載中") -> WebNovelType.连载中
                    else -> throw RuntimeException("无法解析的小说类型:$it")
                }
            }

        val attentions = mutableListOf<WebNovelAttention>()
        val keywords = mutableListOf<String>()
        doc.selectFirst("div.text-xs")!!
            .children()
            .map { it.text() }
            .forEach {
                when (it) {
                    "残酷描写あり" -> attentions.add(WebNovelAttention.残酷描写)
                    "R-15" -> attentions.add(WebNovelAttention.R15)
                    "R-18", "R-18G" -> attentions.add(WebNovelAttention.R18)
                    else -> keywords.add(it)
                }
            }

        val introduction = doc
            .selectFirst("div.my-4 > div.text-sm")!!
            .text()

        val toc = doc.select("div.table-of-contents > ol > li").map { col ->
            val h3 = col.selectFirst("h3 > span")?.text()
            val li = col.select("ol > li").mapNotNull { el ->
                if (el.selectFirst("button") != null) null
                else RemoteNovelMetadata.TocItem(
                    title = el.selectFirst("div.leading-6")!!.text(),
                    chapterId = el.selectFirst("a")!!
                        .attr("href")
                        .removeSuffix("/")
                        .substringAfterLast("/"),
                    createAt =
                    parseJapanDateString(
                        "yyyy年M月d日HH:mm",
                        el.selectFirst("div.text-xs")!!.child(1).text().let {
                            it.substringBefore('(') +
                                    it.substringAfter(' ').substringBefore('(')
                        }
                    ),
                )
            }
            if (h3 == null) li else listOf(
                RemoteNovelMetadata.TocItem(
                    title = h3,
                )
            ) + li
        }.flatten()

        return RemoteNovelMetadata(
            title = title,
            type = type,
            keywords = keywords,
            attentions = attentions,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val doc = client.get("https://novelism.jp/novel/$novelId/article/$chapterId/").document()
        val jsonRaw = doc.select("script")
            .map { it.html() }
            .filter { it.isNotBlank() && "gtm" !in it }
            .maxBy { it.length }
            .let { it.substring(it.indexOf("content:")) }
            .let { it.substring(0, it.indexOf("}]\",") + 2) }
            .removePrefix("content:\"")
            .replace("\\\"", "\"")
        val content = Json.parseToJsonElement(jsonRaw)
            .jsonArray
            .map { it.jsonObject["insert"]!! }
            .joinToString("") { el ->
                (el as? JsonPrimitive)?.content
                    ?: el.jsonObject.let { obj ->
                        obj["ruby"]?.let { ruby ->
                            ruby.jsonObject["rb"]!!.jsonPrimitive.content
                        } ?: obj["block-image"]?.let { img ->
                            "<图片>${img.jsonPrimitive.content}\\n"
                        } ?: "\\n"
                    }
            }
            .split("\\n")
        return RemoteChapter(paragraphs = content)
    }
}