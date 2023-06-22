package infra.provider.providers

import infra.model.WebNovelAttention
import infra.model.WebNovelAuthor
import infra.model.WebNovelType
import infra.provider.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*


class Pixiv : WebNovelProvider {
    companion object {
        const val id = "pixiv"
    }

    private suspend fun get(url: String): HttpResponse {
        return client.get(url) {
            headers {
                append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
                )
            }
            System.getenv("PIXIV_COOKIE_PHPSESSID")?.let {
                cookie(name = "PHPSESSID", value = it, domain = ".pixiv.net")
            }
        }
    }

    override suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        if (novelId.startsWith("s")) {
            val chapterId = novelId.removePrefix("s")
            val url = "https://www.pixiv.net/novel/show.php?id=$chapterId"
            val doc = get(url).document()
            val jsonRaw = doc.selectFirst("meta#meta-preload-data")!!.attr("content")
            val obj = Json.parseToJsonElement(jsonRaw).jsonObject["novel"]!!.jsonObject[chapterId]!!.jsonObject
            assert(obj["seriesNavData"] != null)

            val title = obj["title"]!!.jsonPrimitive.content

            val author = WebNovelAuthor(
                name = obj["userName"]!!.jsonPrimitive.content,
                link = "https://www.pixiv.net/users/" + obj["userId"]!!.jsonPrimitive.content
            )

            val keywords = obj["tags"]!!.jsonObject["tags"]!!.jsonArray
                .map { it.jsonObject["tag"]!!.jsonPrimitive.content }
                .filter { it != "R-18" }

            val attentions = obj["xRestrict"]!!.jsonPrimitive.int
                .let {
                    when (it) {
                        0 -> emptyList()
                        else -> listOf(WebNovelAttention.R18)
                    }
                }

            val introduction = obj["description"]!!.jsonPrimitive.content
                .replace("<br />", "\n")

            return RemoteNovelMetadata(
                title = title,
                authors = listOf(author),
                type = WebNovelType.短篇,
                keywords = keywords,
                attentions = attentions,
                introduction = introduction,
                toc = listOf(
                    RemoteNovelMetadata.TocItem(
                        title = "无名",
                        chapterId = chapterId,
                    )
                ),
            )
        } else {
            val obj1 = get("https://www.pixiv.net/ajax/novel/series/$novelId").json()["body"]!!.jsonObject
            val title = obj1["title"]!!.jsonPrimitive.content
            val author = WebNovelAuthor(
                name = obj1["userName"]!!.jsonPrimitive.content,
                link = "https://www.pixiv.net/users/" + obj1["userId"]!!.jsonPrimitive.content,
            )

            val keywords = obj1["tags"]!!.jsonArray
                .map { it.jsonPrimitive.content }

            val attentions = obj1["xRestrict"]!!.jsonPrimitive.int
                .let {
                    when (it) {
                        0 -> emptyList()
                        else -> listOf(WebNovelAttention.R18)
                    }
                }

            val introduction = obj1["caption"]!!.jsonPrimitive.content

            val obj2 = get("https://www.pixiv.net/ajax/novel/series/$novelId/content_titles").json()
            val toc = obj2["body"]!!.jsonArray
                .map { it.jsonObject }
                .map {
                    RemoteNovelMetadata.TocItem(
                        title = it["title"]!!.jsonPrimitive.content,
                        chapterId = it["id"]!!.jsonPrimitive.content,
                    )
                }

            return RemoteNovelMetadata(
                title = title,
                authors = listOf(author),
                type = WebNovelType.连载中,
                keywords = keywords,
                attentions = attentions,
                introduction = introduction,
                toc = toc,
            )
        }
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url = "https://www.pixiv.net/novel/show.php?id=$chapterId"
        val doc = get(url).document()
        val jsonRaw = doc.selectFirst("meta#meta-preload-data")!!.attr("content")
        val obj = Json.parseToJsonElement(jsonRaw).jsonObject["novel"]!!.jsonObject[chapterId]!!.jsonObject
        return RemoteChapter(paragraphs = obj["content"]!!.jsonPrimitive.content.lines())
    }
}
