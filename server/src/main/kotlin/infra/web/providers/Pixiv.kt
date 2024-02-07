package infra.web.providers

import infra.model.*
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class Pixiv(
    private val client: HttpClient,
) : WebNovelProvider {
    companion object {
        const val id = "pixiv"

        suspend fun addCookies(cookies: CookiesStorage, phpsessid: String?) {
            if (phpsessid != null) {
                cookies.addCookie(
                    "https://www.pixiv.net",
                    Cookie(name = "PHPSESSID", value = phpsessid, domain = ".pixiv.net")
                )
            }
        }
    }

    private suspend fun get(url: String): HttpResponse {
        return client.get(url) {
            headers {
                append(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
                )
            }
        }
    }

    override suspend fun getRank(options: Map<String, String>): Page<RemoteNovelListItem> {
        return emptyPage()
    }

    override suspend fun getMetadata(novelId: String): RemoteNovelMetadata {
        if (novelId.startsWith("s")) {
            val chapterId = novelId.removePrefix("s")
            val url = "https://www.pixiv.net/novel/show.php?id=$chapterId"
            val doc = get(url).document()


            val obj = doc
                .selectFirst("meta#meta-preload-data")!!
                .attr("content")
                .let { Json.parseToJsonElement(it) }
                .jsonObject
                .obj("novel")
                .obj(chapterId)

            val seriesData = obj.objOrNull("seriesNavData")
            if (seriesData != null) {
                val targetNovelId = seriesData.int("seriesId").toString()
                throw NovelIdShouldBeReplacedException(id, targetNovelId)
            }

            val title = obj
                .string("title")

            val author = WebNovelAuthor(
                name = obj.string("userName"),
                link = "https://www.pixiv.net/users/" + obj.string("userId"),
            )

            val keywords = obj
                .obj("tags")
                .array("tags")
                .map { it.jsonObject.string("tag") }
                .filter { it != "R-18" }

            val attentions = obj
                .int("xRestrict")
                .let {
                    if (it == 0) emptyList()
                    else listOf(WebNovelAttention.R18)
                }

            val totalCharacters = obj
                .int("characterCount")

            val introduction = obj
                .string("description")
                .replace("<br />", "\n")

            return RemoteNovelMetadata(
                title = title,
                authors = listOf(author),
                type = WebNovelType.短篇,
                keywords = keywords,
                attentions = attentions,
                points = null,
                totalCharacters = totalCharacters,
                introduction = introduction,
                toc = listOf(
                    RemoteNovelMetadata.TocItem(
                        title = "无名",
                        chapterId = chapterId,
                    )
                ),
            )
        } else {
            val obj1 = get("https://www.pixiv.net/ajax/novel/series/$novelId")
                .json()
                .obj("body")

            val title = obj1
                .string("title")

            val author = WebNovelAuthor(
                name = obj1.string("userName"),
                link = "https://www.pixiv.net/users/" + obj1.string("userId"),
            )

            val keywords = obj1
                .array("tags")
                .map { it.jsonPrimitive.content }

            val attentions = obj1
                .int("xRestrict")
                .let {
                    if (it == 0) emptyList()
                    else listOf(WebNovelAttention.R18)
                }

            val totalCharacters = obj1
                .int("publishedTotalCharacterCount")

            val introduction = obj1
                .string("caption")

            val arr2 = get("https://www.pixiv.net/ajax/novel/series/$novelId/content_titles")
                .json()
                .array("body")

            val toc = arr2
                .map { it.jsonObject }
                .filter { it["available"]!!.jsonPrimitive.boolean }
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
                points = null,
                totalCharacters = totalCharacters,
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
