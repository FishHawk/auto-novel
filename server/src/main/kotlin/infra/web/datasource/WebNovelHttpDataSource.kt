package infra.web.datasource

import infra.common.Page
import infra.web.datasource.providers.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class WebNovelHttpDataSource(
    httpsProxy: String?,
    pixivPhpsessid: String?,
) {
    private val client = HttpClient(Java) {
        install(HttpCookies) {
            default {
                Alphapolis.addCookies(this)
                Hameln.addCookies(this)
                Pixiv.addCookies(this, phpsessid = pixivPhpsessid)
                Syosetu.addCookies(this)
            }
        }
        install(ContentNegotiation) {
            json(Json { isLenient = true })
        }
        expectSuccess = true
        httpsProxy?.let {
            engine {
                proxy = ProxyBuilder.http(it)
            }
        }
        defaultRequest {
            header(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
            )
        }
    }

    val providers = mapOf(
        Alphapolis.id to Alphapolis(client),
        Hameln.id to Hameln(client),
        Kakuyomu.id to Kakuyomu(client),
        Novelup.id to Novelup(client),
        Pixiv.id to Pixiv(client),
        Syosetu.id to Syosetu(client),
    )

    suspend fun listRank(providerId: String, options: Map<String, String>): Result<Page<RemoteNovelListItem>> {
        return runCatching {
            providers[providerId]!!.getRank(options)
        }
    }

    suspend fun getMetadata(providerId: String, novelId: String): Result<RemoteNovelMetadata> {
        return runCatching {
            rejectFascistNovel(providerId, novelId)
            providers[providerId]!!.getMetadata(novelId)
        }
    }

    suspend fun getChapter(providerId: String, novelId: String, chapterId: String): Result<RemoteChapter> {
        return runCatching {
            rejectFascistNovel(providerId, novelId)
            providers[providerId]!!.getChapter(novelId, chapterId)
        }
    }
}

private val disgustingFascistNovelList = mapOf(
    "syosetu" to listOf(
        "n0646ie",
    ),
    "kakuyomu" to listOf(
        "16817330660019717771",
    )
)

private fun rejectFascistNovel(providerId: String, novelId: String) {
    disgustingFascistNovelList.get(providerId)?.let {
        if (novelId in it) {
            throw Exception("该小说包含法西斯内容，不予显示")
        }
    }
}
