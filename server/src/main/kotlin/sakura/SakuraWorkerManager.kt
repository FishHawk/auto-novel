package sakura

import infra.DataSourceMongo
import domain.entity.SakuraServer
import infra.web.WebNovelChapterRepository
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.serialization.json.Json
import org.bson.types.ObjectId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class SakuraWorkerManager(
    private val mongo: DataSourceMongo,
    private val webChapterRepo: WebNovelChapterRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
    private val wenkuVolumeRepo: WenkuNovelVolumeRepository,
) {
    private val client = HttpClient(Java) {
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 5)
            exponentialDelay(base = 4.0)
        }
        install(ContentNegotiation) {
            json(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        expectSuccess = true
    }

    private val _workers = mutableMapOf<String, SakuraWorker>()
    val workers: Map<String, SakuraWorker>
        get() = _workers

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun addWorker(
        server: SakuraServer,
    ) {
        val worker = SakuraWorker(
            scope = scope,
            server = server,
            client = client,
            mongo = mongo,
            webChapterRepo = webChapterRepo,
            wenkuMetadataRepo = wenkuMetadataRepo,
            wenkuVolumeRepo = wenkuVolumeRepo,
        )
        _workers[worker.id] = worker
    }

    init {
        scope.launch {
            return@launch
            delay(15.seconds.toJavaDuration())
            val servers = mongo
                .sakuraServerCollection
                .find()
                .toList()
            servers.forEach {
                addWorker(it)
                if (it.isActive) {
                    delay(1.seconds.toJavaDuration())
                }
            }
        }
    }

    suspend fun createWorker(
        username: String,
        gpu: String,
        endpoint: String,
    ) {
        throw RuntimeException("暂停使用")
        val server = SakuraServer(
            id = ObjectId(),
            username = username,
            gpu = gpu,
            endpoint = endpoint,
        )
        val id = mongo
            .sakuraServerCollection
            .insertOne(server)
            .insertedId!!
            .asObjectId().value

        addWorker(server.copy(id = id))
    }

    suspend fun startWorker(id: String) {
        throw RuntimeException("暂停使用")
        _workers[id]?.start()
    }

    suspend fun stopWorker(id: String) {
        throw RuntimeException("暂停使用")
        _workers[id]?.stop()
    }

    suspend fun deleteWorker(id: String) {
        throw RuntimeException("暂停使用")
        mongo
            .sakuraServerCollection
            .deleteOneById(ObjectId(id))
        _workers[id]?.stop()
        _workers.remove(id)
    }
}