package infra

import infra.provider.RemoteChapter
import infra.provider.RemoteNovelListItem
import infra.provider.RemoteNovelMetadata
import infra.provider.providers.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class DataSourceWebNovelProvider(
    httpsProxy: String?,
    pixivPhpsessid: String?,
) {
    private val cookies = AcceptAllCookiesStorage()

    private val client = HttpClient(Java) {
        install(HttpCookies) { storage = cookies }
        install(ContentNegotiation) {
            json(Json { isLenient = true })
        }
        expectSuccess = true
        httpsProxy?.let {
            engine {
                proxy = ProxyBuilder.http(it)
            }
        }
    }

    // Ktor的ContentNegotiation会影响Accept头
    // 进一步测试后可能可以去掉这个client
    private val clientText = HttpClient(Java) {
        install(HttpCookies) { storage = cookies }
        expectSuccess = true
        httpsProxy?.let {
            engine {
                proxy = ProxyBuilder.http(it)
            }
        }
    }

    private val providers = mapOf(
        Alphapolis.id to Alphapolis(clientText, cookies),
        Hameln.id to Hameln(client, cookies),
        Kakuyomu.id to Kakuyomu(client),
        Novelism.id to Novelism(client),
        Novelup.id to Novelup(client),
        Pixiv.id to Pixiv(client, cookies, phpsessid = pixivPhpsessid),
        Syosetu.id to Syosetu(client, cookies),
    )

    suspend fun listRank(providerId: String, options: Map<String, String>): Result<List<RemoteNovelListItem>> {
        return runCatching {
            providers[providerId]!!.getRank(options)
        }
    }

    suspend fun getMetadata(providerId: String, novelId: String): Result<RemoteNovelMetadata> {
        return runCatching {
            providers[providerId]!!.getMetadata(novelId)
        }
    }

    suspend fun getChapter(providerId: String, novelId: String, chapterId: String): Result<RemoteChapter> {
        return runCatching {
            providers[providerId]!!.getChapter(novelId, chapterId)
        }
    }
}