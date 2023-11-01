package sakura

import infra.DataSourceMongo
import infra.model.*
import infra.web.WebNovelChapterRepository
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Serializable
data class SakuraWorkerProgress(
    val total: Int,
    val finished: Int,
    val error: Int,
)

class SakuraWorker(
    private val scope: CoroutineScope,
    server: SakuraServer,
    private val client: HttpClient,
    private val mongo: DataSourceMongo,
    private val chapterRepo: WebNovelChapterRepository,
) {
    private var job: Job? = null

    init {
        if (server.isActive) {
            job = createRunningJob()
        }
    }

    val id: String = server.id.toHexString()
    val gpu = server.gpu
    val endpoint = server.endpoint
    val isActive
        get() = job?.isActive == true

    var description: String = ""
        private set

    var progress: SakuraWorkerProgress? = null
        private set

    suspend fun start() {
        if (isActive) return
        mongo
            .sakuraServerCollection
            .updateOneById(
                ObjectId(id),
                setValue(SakuraServer::isActive, true),
            )
        job = createRunningJob()
    }

    suspend fun stop() {
        if (!isActive) return
        mongo
            .sakuraServerCollection
            .updateOneById(
                ObjectId(id),
                setValue(SakuraServer::isActive, false),
            )
        job?.cancelAndJoin()
        releaseWorkingSakuraJob()
    }

    private fun createRunningJob(): Job {
        val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
            scope.launch { releaseWorkingSakuraJob() }
            println("Sakura worker error: $id")
            throwable.printStackTrace()
        }
        return scope.launch(coroutineExceptionHandler) {
            run()
        }
    }

    private suspend fun releaseWorkingSakuraJob() {
        mongo
            .sakuraJobCollection
            .updateMany(
                SakuraJob::workerId eq id,
                setValue(SakuraJob::workerId, null),
            )
    }

    private suspend fun run() {
        while (true) {
            val job = mongo
                .sakuraJobCollection
                .findOne(SakuraJob::workerId eq id)
                ?: mongo
                    .sakuraJobCollection
                    .findOneAndUpdate(
                        SakuraJob::workerId eq null,
                        setValue(SakuraJob::workerId, id),
                    )
            if (job == null) {
                delay(10.seconds.toJavaDuration())
                continue
            }
            try {
                processJob(job)

                mongo
                    .sakuraJobCollection
                    .deleteOne(SakuraJob::id eq job.id)

                mongo
                    .sakuraJobResultCollection
                    .insertOne(
                        SakuraJobResult(
                            task = job.task,
                            description = job.description,
                            workerId = id,
                            submitter = job.submitter,
                            total = progress?.total,
                            finished = progress?.finished,
                            error = progress?.error,
                            createAt = job.createAt,
                            finishAt = Clock.System.now(),
                        )
                    )
            } catch (e: Throwable) {
                mongo
                    .sakuraJobCollection
                    .updateMany(
                        SakuraJob::workerId eq id,
                        setValue(SakuraJob::workerId, null),
                    )
                if (e is CancellationException) throw e
                else if (e is SakuraNetworkException) stop()
            } finally {
                progress = null
                description = ""
            }
        }
    }

    private suspend fun processJob(job: SakuraJob) {
        this.description = job.task + "\n" + job.description

        val taskUrl = URLBuilder().takeFrom(job.task).build()
        when (taskUrl.pathSegments.first()) {
            "web" -> processWebTranslateJob(taskUrl)
            "wenku" -> processWenkuTranslateJob(taskUrl)
        }
    }

    private suspend fun processWebTranslateJob(
        taskUrl: Url,
    ) {
        val (_, providerId, novelId) = taskUrl.pathSegments
        val start = taskUrl.parameters["start"]?.toIntOrNull() ?: 0
        val end = taskUrl.parameters["end"]?.toIntOrNull() ?: 65536

        @Serializable
        data class WebNovelChapterProjection(
            @SerialName("episodeId")
            val chapterId: String,
        )

        val novel = mongo
            .webNovelMetadataCollection
            .findOne(
                WebNovelChapter::providerId eq providerId,
                WebNovelChapter::novelId eq novelId,
            ) ?: return

        val translatedChapterId = mongo
            .webNovelChapterCollection
            .withDocumentClass<WebNovelChapterProjection>()
            .find(
                WebNovelChapter::providerId eq providerId,
                WebNovelChapter::novelId eq novelId,
                WebNovelChapter::sakuraParagraphs ne null,
            )
            .projection(WebNovelChapterProjection::chapterId)
            .toList()
            .map { it.chapterId }

        val untranslatedChapterId = novel
            .toc
            .mapNotNull { it.chapterId }
            .filterIndexed { index, _ -> index in start..<end }
            .filterNot { it in translatedChapterId }
            .let {
                it.subList(0, it.size.coerceAtMost(300))
            }

        val total = untranslatedChapterId.size
        var finished = 0
        var error = 0

        fun updateProgress() {
            this.progress = SakuraWorkerProgress(
                total = total,
                finished = finished,
                error = error,
            )
        }

        updateProgress()
        untranslatedChapterId.forEach { chapterId ->
            try {
                val chapter = chapterRepo.getOrSyncRemote(
                    providerId = providerId,
                    novelId = novelId,
                    chapterId = chapterId,
                    forceSync = false,
                ).getOrElse {
                    error += 1
                    updateProgress()
                    return@forEach
                }

                val sakuraParagraphs = sakuraTranslate(
                    client = client,
                    endpoint = endpoint,
                    input = chapter.paragraphs,
                ) { prompt, result ->
                    mongo
                        .sakuraFailCaseCollection
                        .insertOne(
                            SakuraFailCase(
                                providerId = providerId,
                                novelId = novelId,
                                chapterId = chapterId,
                                prompt = prompt,
                                result = result,
                            )
                        )
                }
                mongo
                    .webNovelChapterCollection
                    .updateOne(
                        and(
                            WebNovelChapter::providerId eq providerId,
                            WebNovelChapter::novelId eq novelId,
                            WebNovelChapter::chapterId eq chapterId,
                        ),
                        setValue(WebNovelChapter::sakuraParagraphs, sakuraParagraphs)
                    )

                val zh = mongo.webNovelChapterCollection
                    .countDocuments(
                        and(
                            WebNovelChapter::providerId eq providerId,
                            WebNovelChapter::novelId eq novelId,
                            WebNovelChapter::sakuraParagraphs ne null,
                        )
                    )
                mongo
                    .webNovelMetadataCollection
                    .updateOne(
                        WebNovelMetadata.byId(providerId, novelId),
                        combine(
                            setValue(WebNovelMetadata::sakura, zh),
                            setValue(WebNovelMetadata::changeAt, Clock.System.now()),
                        ),
                    )

                finished += 1
                updateProgress()
            } catch (e: Throwable) {
                when (e) {
                    is SakuraNetworkException, is CancellationException -> {
                        throw e
                    }

                    else -> {
                        error += 1
                        updateProgress()
                    }
                }
            }
        }
    }

    private suspend fun processWenkuTranslateJob(
        taskUrl: Url,
    ) {
        val (_, novelId) = taskUrl.pathSegments
        val start = taskUrl.parameters["start"]?.toIntOrNull() ?: 0
        val end = taskUrl.parameters["end"]?.toIntOrNull() ?: 65536
    }
}