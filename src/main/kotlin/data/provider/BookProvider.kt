package data.provider

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class SBookAuthor(
    val name: String,
    val link: String? = null,
)

data class SBookTocItem(
    val title: String,
    val episodeId: String? = null,
)

data class SBookMetadata(
    val title: String,
    val authors: List<SBookAuthor>,
    val introduction: String,
    val toc: List<SBookTocItem>,
)

data class SBookEpisode(
    val paragraphs: List<String>,
)

data class SBookListItem(
    val bookId: String,
    val title: String,
    val extra: String,
)

interface BookProvider {
    suspend fun getRank(options: Map<String, String>): List<SBookListItem>

    fun getMetadataUrl(bookId: String): String
    fun getEpisodeUrl(bookId: String, episodeId: String): String

    suspend fun getMetadata(bookId: String): SBookMetadata
    suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode
}

val cookies = AcceptAllCookiesStorage()

val client = HttpClient(Java) {
    install(HttpCookies) { storage = cookies }
    install(ContentNegotiation) {
        json(Json { isLenient = true })
    }
    expectSuccess = true
    System.getenv("HTTPS_PROXY")?.let {
        engine {
            proxy = ProxyBuilder.http(it)
        }
    }
}

suspend fun HttpResponse.document(): Document = Jsoup.parse(body<String>())
suspend fun HttpResponse.json(): JsonObject = body()
