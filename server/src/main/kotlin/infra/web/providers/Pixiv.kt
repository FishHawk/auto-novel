package infra.web.providers

import domain.entity.*
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

            val toc = mutableListOf<RemoteNovelMetadata.TocItem>()
            val keywords = mutableListOf<String>()

            obj1
                .array("tags")
                .forEach { keywords.add(it.jsonPrimitive.content) }

            if (keywords.isEmpty()) {
                val arr1 =
                    get("https://www.pixiv.net/ajax/novel/series_content/${novelId}?limit=30&last_order=0&order_by=asc")
                        .json()
                        .obj("body")
                        .obj("page")
                        .array("seriesContents")

                val keywordsBuffer = mutableSetOf<String>()
                arr1
                    .map { it.jsonObject }
                    .forEach { seriesContent ->
                        if (seriesContent.containsKey("title")) {
                            keywordsBuffer.addAll(
                                seriesContent
                                    .array("tags")
                                    .map { it.jsonPrimitive.content }
                            )
                            toc.add(
                                RemoteNovelMetadata.TocItem(
                                    title = seriesContent.string("title"),
                                    chapterId = seriesContent.string("id"),
                                )
                            )
                        }
                    }
                keywords.addAll(keywordsBuffer)

                if (arr1.size < 30) {
                    // 只有一页
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

            toc.clear()
            val arr2 = get("https://www.pixiv.net/ajax/novel/series/$novelId/content_titles")
                .json()
                .array("body")

            arr2
                .map { it.jsonObject }
                .filter { it.boolean("available") }
                .forEach {
                    toc.add(
                        RemoteNovelMetadata.TocItem(
                            title = it.string("title"),
                            chapterId = it.string("id"),
                        )
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

    private val imagePattern1 = """\[uploadedimage:(\d+)]""".toRegex()
    private val imagePattern2 = """\[pixivimage:(\d+)]""".toRegex()

    private fun parseImageUrlPattern1(line: String, embeddedImages: JsonObject?): String? {
        embeddedImages ?: return null
        val id = imagePattern1.find(line)?.groupValues?.get(1) ?: return null
        val url = embeddedImages.obj(id).obj("urls").string("original")
        return url
    }

    private suspend fun parseImageUrlPattern2(line: String, chapterId: String): String? {
        val id = imagePattern2.find(line)?.groupValues?.get(1) ?: return null
        val fetchUrl = "https://www.pixiv.net/ajax/novel/${chapterId}/insert_illusts?id%5B%5D=${id}"
        val body = get(fetchUrl).json().obj("body")
        val url = body.obj(id).obj("illust").obj("images").string("original")
        return url
    }

    private val rubyPattern = """\[\[rb:([^>]+) > ([^]]+)]]""".toRegex()
    private fun cleanRuby(line: String): String {
        return line.replace(rubyPattern) { it.groupValues[1] }
    }

    override suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter {
        val url = "https://www.pixiv.net/ajax/novel/$chapterId"
        val body = get(url).json().obj("body")

        val embeddedImages = body.objOrNull("textEmbeddedImages")
        val content = body.string("content")

        val paragraphs = content.lines().map { line ->
            val imageUrl = parseImageUrlPattern1(line, embeddedImages)
                ?: parseImageUrlPattern2(line, chapterId)

            if (imageUrl == null) {
                cleanRuby(line)
            } else {
                "<图片>${imageUrl}"
            }
        }
        return RemoteChapter(paragraphs = paragraphs)
    }
}
