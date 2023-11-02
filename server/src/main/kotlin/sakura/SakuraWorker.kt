package sakura

import infra.DataSourceMongo
import infra.model.*
import infra.web.WebNovelChapterRepository
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Serializable
data class SakuraWorkerProgress(
    val total: Int,
    val finished: Int,
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
        job?.cancelAndJoin()
        mongo
            .sakuraServerCollection
            .updateOneById(
                ObjectId(id),
                setValue(SakuraServer::isActive, false),
            )
        mongo
            .sakuraJobCollection
            .updateMany(
                SakuraJob::workerId eq id,
                setValue(SakuraJob::workerId, null),
            )
        progress = null
        description = ""
    }

    private fun createRunningJob(): Job {
        return scope.launch {
            try {
                run()
            } catch (e: Throwable) {
                mongo
                    .sakuraServerCollection
                    .updateOneById(
                        ObjectId(id),
                        setValue(SakuraServer::isActive, false),
                    )
                mongo
                    .sakuraJobCollection
                    .updateMany(
                        SakuraJob::workerId eq id,
                        setValue(SakuraJob::workerId, null),
                    )
                progress = null
                if (e !is CancellationException) {
                    description = e.toString()
                }
                ensureActive()
            }
        }
    }

    private suspend fun run() {
        while (true) {
            val sakuraJob = mongo
                .sakuraJobCollection
                .findOne(SakuraJob::workerId eq id)
                ?: mongo
                    .sakuraJobCollection
                    .findOneAndUpdate(
                        SakuraJob::workerId eq null,
                        setValue(SakuraJob::workerId, id),
                    )
            if (sakuraJob == null) {
                delay(60.seconds.toJavaDuration())
                continue
            }

            executeJob(sakuraJob)

            mongo
                .sakuraJobCollection
                .deleteOne(SakuraJob::id eq sakuraJob.id)
            mongo
                .sakuraJobResultCollection
                .insertOne(
                    SakuraJobResult(
                        task = sakuraJob.task,
                        description = sakuraJob.description,
                        workerId = id,
                        submitter = sakuraJob.submitter,
                        total = progress?.total,
                        finished = progress?.finished,
                        createAt = sakuraJob.createAt,
                        finishAt = Clock.System.now(),
                    )
                )

            progress = null
            description = ""
        }
    }

    private suspend fun executeJob(job: SakuraJob) {
        val taskUrl = URLBuilder().takeFrom(job.task).build()
        when (taskUrl.pathSegments.first()) {
            "web" -> processWebTranslateJob(job, taskUrl)
            "wenku" -> processWenkuTranslateJob(taskUrl)
        }
    }

    private suspend fun processWebTranslateJob(
        job: SakuraJob,
        taskUrl: Url,
    ) {
        val (_, providerId, novelId) = taskUrl.pathSegments
        val start = taskUrl.parameters["start"]?.toIntOrNull() ?: 0
        val end = taskUrl.parameters["end"]?.toIntOrNull() ?: 65536
        val shouldTranslateExpiredChapter = true

        this.description = job.description + "\n" + "${providerId}/${novelId}"

        val novel = mongo
            .webNovelMetadataCollection
            .findOne(
                WebNovelChapter::providerId eq providerId,
                WebNovelChapter::novelId eq novelId,
            ) ?: return

        val chapterTranslationOutlines =
            chapterRepo.getTranslationOutlines(
                providerId = providerId,
                novelId = novelId,
                translatorId = TranslatorId.Sakura,
            )
        val chapters = novel.toc
            .mapNotNull { it.chapterId }
            .filterIndexed { index, _ -> index in start..<end }
            .filter { chapterId ->
                val chapterTranslationOutline = chapterTranslationOutlines.find {
                    it.chapterId == chapterId
                }
                if (chapterTranslationOutline?.translated != true) {
                    // 未翻译
                    true
                } else if (chapterTranslationOutline.glossaryUuid == novel.glossaryUuid) {
                    // 翻译未过期
                    false
                } else {
                    // 翻译已过期
                    shouldTranslateExpiredChapter
                }
            }
            .let {
                it.subList(0, it.size.coerceAtMost(300))
            }

        val total = chapters.size
        var finished = 0

        fun updateProgress() {
            this.progress = SakuraWorkerProgress(
                total = total,
                finished = finished,
            )
        }

        updateProgress()

        val filteredGlossary = novel
            .glossary
            .filterKeys { it.length >= 3 }

        chapters.forEach { chapterId ->
            this.description = job.description + "\n" + "${providerId}/${novelId}/${chapterId}"
            val chapter = chapterRepo.getOrSyncRemote(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                forceSync = false,
            ).getOrThrow()

            val paragraphs = chapter.paragraphs.map {
                var line = it
                filteredGlossary.forEach { (jpWord, zhWord) ->
                    line = line.replace(jpWord, zhWord)
                }
                line
            }
            val sakuraParagraphs = sakuraTranslate(
                client = client,
                endpoint = endpoint,
                input = paragraphs,
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

            chapterRepo.updateTranslation(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                translatorId = TranslatorId.Sakura,
                glossary = novel.glossaryUuid?.let { Glossary(it, novel.glossary) },
                paragraphsZh = sakuraParagraphs,
            )

            finished += 1
            updateProgress()
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