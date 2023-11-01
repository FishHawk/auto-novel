package sakura

import infra.DataSourceMongo
import infra.model.SakuraServer
import infra.web.WebNovelChapterRepository
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
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
    private val webNovelChapterRepo: WebNovelChapterRepository,
) {
    private val client = HttpClient(Java) {
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

    init {
        scope.launch {
            delay(10.seconds.toJavaDuration())
            val servers = mongo
                .sakuraServerCollection
                .find()
                .toList()
            servers.forEach {
                val worker = SakuraWorker(
                    scope = scope,
                    server = it,
                    client = client,
                    mongo = mongo,
                    chapterRepo = webNovelChapterRepo,
                )
                _workers[worker.id] = worker
                delay(1.seconds.toJavaDuration())
            }
        }
    }

    suspend fun createWorker(
        gpu: String,
        endpoint: String,
    ) {
        val server = SakuraServer(
            id = ObjectId(),
            gpu = gpu,
            endpoint = endpoint,
        )
        val id = mongo
            .sakuraServerCollection
            .insertOne(server)
            .insertedId!!
            .asObjectId().value

        val worker = SakuraWorker(
            scope = scope,
            server = server.copy(id = id),
            client = client,
            mongo = mongo,
            chapterRepo = webNovelChapterRepo,
        )
        _workers[worker.id] = worker
    }

    suspend fun startWorker(id: String) {
        _workers[id]?.start()
    }

    suspend fun stopWorker(id: String) {
        _workers[id]?.stop()
    }

    suspend fun deleteWorker(id: String) {
        mongo
            .sakuraServerCollection
            .deleteOneById(ObjectId(id))
        _workers[id]?.stop()
        _workers.remove(id)
    }
}