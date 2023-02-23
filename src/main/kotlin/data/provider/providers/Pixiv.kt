package data.provider.providers

import data.provider.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


class Pixiv : BookProvider {
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

    override suspend fun getRank(options: Map<String, String>): List<SBookListItem> {
        TODO("Not yet implemented")
    }

    override fun getMetadataUrl(bookId: String): String {
        return if (bookId.startsWith("s")) {
            getEpisodeUrl(bookId, bookId.removePrefix("s"))
        } else {
            "https://www.pixiv.net/novel/series/${bookId}"
        }
    }

    override fun getEpisodeUrl(bookId: String, episodeId: String): String {
        return "https://www.pixiv.net/novel/show.php?id=$episodeId"
    }

    override suspend fun getMetadata(bookId: String): SBookMetadata {
        if (bookId.startsWith("s")) {
            val episodeId = bookId.removePrefix("s")
            val doc = get(getEpisodeUrl(bookId, episodeId)).document()
            val jsonRaw = doc.selectFirst("meta#meta-preload-data")!!.attr("content")
            val obj = Json.parseToJsonElement(jsonRaw).jsonObject["novel"]!!.jsonObject[episodeId]!!.jsonObject
            assert(obj["seriesNavData"] != null)

            val title = obj["title"]!!.jsonPrimitive.content
            val author = SBookAuthor(
                name = obj["userName"]!!.jsonPrimitive.content,
                link = "https://www.pixiv.net/users/" + obj["userId"]!!.jsonPrimitive.content
            )
            val introduction = obj["description"]!!.jsonPrimitive.content.replace("<br />", "\n")

            return SBookMetadata(
                title = title,
                authors = listOf(author),
                introduction = introduction,
                toc = listOf(SBookTocItem(title = "无名", episodeId = episodeId)),
            )
        } else {
            val obj1 = get("https://www.pixiv.net/ajax/novel/series/$bookId").json()["body"]!!.jsonObject
            val title = obj1["title"]!!.jsonPrimitive.content
            val author = SBookAuthor(
                name = obj1["userName"]!!.jsonPrimitive.content,
                link = "https://www.pixiv.net/users/" + obj1["userId"]!!.jsonPrimitive.content
            )
            val introduction = obj1["caption"]!!.jsonPrimitive.content

            val obj2 = get("https://www.pixiv.net/ajax/novel/series/$bookId/content_titles").json()
            val toc = obj2["body"]!!.jsonArray
                .map { it.jsonObject }
                .map {
                    SBookTocItem(
                        title = it["title"]!!.jsonPrimitive.content,
                        episodeId = it["id"]!!.jsonPrimitive.content,
                    )
                }

            return SBookMetadata(
                title = title,
                authors = listOf(author),
                introduction = introduction,
                toc = toc,
            )
        }
    }

    override suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode {
        val doc = get(getEpisodeUrl(bookId, episodeId)).document()
        val jsonRaw = doc.selectFirst("meta#meta-preload-data")!!.attr("content")
        val obj = Json.parseToJsonElement(jsonRaw).jsonObject["novel"]!!.jsonObject[episodeId]!!.jsonObject
        return SBookEpisode(paragraphs = obj["content"]!!.jsonPrimitive.content.lines())
    }
}
