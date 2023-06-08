package infra.provider.providers

import infra.provider.*
import io.ktor.client.request.*
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat

class Novelism : WebNovelProvider {
    companion object {
        const val id = "novelism"
    }

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        return emptyList()
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        val doc = client.get("https://novelism.jp/novel/$novelId").document()

        val title = doc.selectFirst("h1.mb-2 > span")!!.text()

        val author = doc
            .selectFirst("div.mb-1 > a.text-sm")!!
            .let {
                RemoteNovelMetadata.Author(
                    name = it.text(),
                    link = "https://novelism.jp" + it.attr("href"),
                )
            }

        val introduction = doc.selectFirst("div.my-4 > div.text-sm")!!.text()

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
                    createAt = SimpleDateFormat("yyyy年M月d日HH:mm").parse(
                        el.selectFirst("div.text-xs")!!.child(1).text().let {
                            it.substringBefore('(') +
                                    it.substringAfter(' ').substringBefore('(')
                        }
                    ).toInstant().toKotlinInstant(),
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