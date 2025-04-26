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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong


class TokenBucketRateLimiter(
    private val capacity: Long,
    private val refillRate: Double  // 令牌/毫秒
) {
    private val tokens = AtomicLong(capacity)
    private var lastRefillTime = Clock.System.now().epochSeconds

    @Synchronized
    fun tryAcquire(): Boolean {
        refillTokens()
        return if (tokens.get() > 0) {
            tokens.decrementAndGet()
            true
        } else {
            false
        }
    }

    private fun refillTokens() {
        val now = Clock.System.now().epochSeconds
        val elapsed = now - lastRefillTime
        val newTokens = (elapsed * refillRate).toLong()
        if (newTokens > 0) {
            tokens.set(minOf(capacity, tokens.get() + newTokens))
            lastRefillTime = now
        }
    }

    fun cooldown() {
        val now = Clock.System.now().epochSeconds
        lastRefillTime = now + 10
        tokens.set(0)
    }
}

class WebNovelHttpDataSource(
    httpsProxy: String?,
    hamelnToken: String?,
    pixivPhpsessid: String?,
) {
    private val client = HttpClient(Java) {
        install(HttpCookies) {
            default {
                Alphapolis.addCookies(this)
                Hameln.addCookies(this, token = hamelnToken ?: "")
                Pixiv.addCookies(this, phpsessid = pixivPhpsessid ?: "")
                Syosetu.addCookies(this)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
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
        Hameln.id to Hameln(client, useProxy = hamelnToken != null),
        Kakuyomu.id to Kakuyomu(client),
        Novelup.id to Novelup(client),
        Pixiv.id to Pixiv(client),
        Syosetu.id to Syosetu(client),
    )
    val limiters = mapOf(
        // Alphapolis.id to TokenBucketRateLimiter(20, 0.1),
        Hameln.id to TokenBucketRateLimiter(20, 0.1),
        // Kakuyomu.id to TokenBucketRateLimiter(20, 0.1),
        // Novelup.id to TokenBucketRateLimiter(20, 0.1),
        Pixiv.id to TokenBucketRateLimiter(20, 0.1),
        // Syosetu.id to TokenBucketRateLimiter(20, 0.1),
    )

    private suspend fun <T> doActionWithLimiter(
        providerId: String,
        block: suspend (WebNovelProvider) -> T,
    ): Result<T> {
        val provider = providers[providerId]!!
        val limiter = limiters[providerId]
        return runCatching {
            if (limiter != null && !limiter.tryAcquire()) {
                throw NovelRateLimitedException()
            }
            block(provider)
        }.onFailure {
            if (it !is WebNovelProviderException) {
                limiter?.cooldown()
            }
        }
    }

    private suspend fun <T> doAction(
        providerId: String,
        block: suspend (WebNovelProvider) -> T,
    ): Result<T> {
        val provider = providers[providerId]!!
        return runCatching {
            block(provider)
        }
    }

    suspend fun listRank(providerId: String, options: Map<String, String>): Result<Page<RemoteNovelListItem>> =
        doAction(providerId) {
            it.getRank(options)
        }

    suspend fun getMetadata(providerId: String, novelId: String): Result<RemoteNovelMetadata> =
        doActionWithLimiter(providerId) {
            rejectFascistNovel(providerId, novelId)
            it.getMetadata(novelId)
        }

    suspend fun getChapter(providerId: String, novelId: String, chapterId: String): Result<RemoteChapter> =
        doAction(providerId) {
            rejectFascistNovel(providerId, novelId)
            it.getChapter(novelId, chapterId)
        }
}

private val disgustingFascistNovelList = mapOf(
    Syosetu.id to listOf(
        "n0646ie",
        "n8926ic",
        "n4583he",
    ),
    Kakuyomu.id to listOf(
        "16817330660019717771",
        "1177354054901629921",
        "16818093082836701336",
    ),
    Hameln.id to listOf(
        "291561",
        "1472",
    ),
)

private fun rejectFascistNovel(providerId: String, novelId: String) {
    disgustingFascistNovelList.get(providerId)?.let {
        if (novelId in it) {
            throw Exception("该小说包含法西斯内容，不予显示")
        }
    }
}
