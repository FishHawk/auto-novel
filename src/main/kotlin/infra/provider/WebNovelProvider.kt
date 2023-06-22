package infra.provider

import infra.model.WebNovelAttention
import infra.model.WebNovelAuthor
import infra.model.WebNovelType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class RemoteNovelMetadata(
    val title: String,
    val authors: List<WebNovelAuthor>,
    val type: WebNovelType,
    val attentions: List<WebNovelAttention>,
    val keywords: List<String>,
    val introduction: String,
    val toc: List<TocItem>,
) {
    data class TocItem(
        val title: String,
        val chapterId: String? = null,
        val createAt: Instant? = null,
    )
}

data class RemoteChapter(
    val paragraphs: List<String>,
)

data class RemoteNovelListItem(
    val novelId: String,
    val title: String,
    val meta: String,
)

interface WebNovelProvider {
    suspend fun getRank(options: Map<String, String>): List<RemoteNovelListItem>
    suspend fun getMetadata(novelId: String): RemoteNovelMetadata
    suspend fun getChapter(novelId: String, chapterId: String): RemoteChapter
}

val cookies = AcceptAllCookiesStorage()

val client = HttpClient(Java) {
    install(HttpCookies) { storage = cookies }
    install(ContentNegotiation) {
        json(Json { isLenient = true })
    }
    expectSuccess = true
    engine {
        proxy = ProxyBuilder.http(
            System.getenv("HTTPS_PROXY") ?: "http://127.0.0.1:7890"
        )
    }
}

// Ktor的ContentNegotiation会影响Accept头
// 进一步测试后可能可以去掉这个client
val clientText = HttpClient(Java) {
    install(HttpCookies) { storage = cookies }
    expectSuccess = true
    engine {
        proxy = ProxyBuilder.http(
            System.getenv("HTTPS_PROXY") ?: "http://127.0.0.1:7890"
        )
    }
}

suspend fun HttpResponse.document(): Document = Jsoup.parse(body<String>())
suspend fun HttpResponse.json(): JsonObject = body()
