package sakura

import infra.DataSourceMongo
import infra.model.*
import infra.web.WebNovelChapterRepository
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.and
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.slf4j.LoggerFactory
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
    private val webChapterRepo: WebNovelChapterRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
    private val wenkuVolumeRepo: WenkuNovelVolumeRepository,
) {
    private var job: Job? = null

    init {
        if (server.isActive) {
            job = createRunningJob()
        }
    }

    val id: String = server.id.toHexString()
    val username = server.username
    val gpu = server.gpu
    val endpoint = server.endpoint
    val isActive
        get() = job?.isActive == true

    var description: String = ""
        private set

    var progress: SakuraWorkerProgress? = null
        private set

    private val logger = LoggerFactory.getLogger("sakura.${id}")

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
            logger.info("启动Worker")
            try {
                run()
            } catch (e: Throwable) {
                logger.info("出错停止，由于${e}")
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
            logger.info("获取Job")
            val sakuraJob =
                mongo
                    .sakuraJobCollection
                    .findOne(SakuraJob::workerId eq id)
                    ?: mongo
                        .sakuraJobCollection
                        .findOneAndUpdate(
                            and(
                                SakuraJob::workerId eq null,
                                SakuraJob::submitter eq username,
                            ),
                            setValue(SakuraJob::workerId, id),
                        )
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

            logger.info("执行Job ${sakuraJob.task}")
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
            "wenku" -> processWenkuTranslateJob(job, taskUrl)
        }
    }

    private suspend fun processWebTranslateJob(
        job: SakuraJob,
        taskUrl: Url,
    ) {
        val (_, providerId, novelId) = taskUrl.pathSegments
        val start = taskUrl.parameters["start"]?.toIntOrNull() ?: 0
        val end = taskUrl.parameters["end"]?.toIntOrNull() ?: 65536
        val shouldTranslateExpiredChapter = taskUrl.parameters["expire"]?.toBoolean() ?: false

        this.description = job.description + "\n" + "${providerId}/${novelId}"

        logger.info("获取Web小说")
        val novel = mongo
            .webNovelMetadataCollection
            .findOne(
                WebNovelChapter::providerId eq providerId,
                WebNovelChapter::novelId eq novelId,
            ) ?: return
        val filteredGlossary = novel
            .glossary
            .filterKeys { it.length >= 3 }

        logger.info("构造需要翻译的章节列表")
        val chapterTranslationOutlines =
            webChapterRepo.getTranslationOutlines(
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
                it.subList(0, it.size.coerceAtMost(150))
            }

        logger.info("开始翻译")
        val total = chapters.size
        var finished = 0

        fun updateProgress() {
            this.progress = SakuraWorkerProgress(
                total = total,
                finished = finished,
            )
        }

        chapters.forEach { chapterId ->
            updateProgress()
            this.description = job.description + "\n" + "${providerId}/${novelId}/${chapterId}"

            logger.info("获取章节 ${providerId}/${novelId}/${chapterId}")
            val chapter = webChapterRepo.getOrSyncRemote(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                forceSync = false,
            ).getOrThrow()

            logger.info("翻译章节 ${providerId}/${novelId}/${chapterId}")
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
                    .sakuraWebFailCaseCollection
                    .insertOne(
                        SakuraWebFailCase(
                            providerId = providerId,
                            novelId = novelId,
                            chapterId = chapterId,
                            prompt = prompt,
                            result = result,
                        )
                    )
            }

            logger.info("更新章节 ${providerId}/${novelId}/${chapterId}")
            webChapterRepo.updateTranslation(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                translatorId = TranslatorId.Sakura,
                glossary = novel.glossaryUuid?.let { Glossary(it, novel.glossary) },
                paragraphsZh = sakuraParagraphs,
            )
            finished += 1
        }
    }

    private suspend fun processWenkuTranslateJob(
        job: SakuraJob,
        taskUrl: Url,
    ) {
        val (_, novelId, volumeId) = taskUrl.pathSegments
        val start = taskUrl.parameters["start"]?.toIntOrNull() ?: 0
        val end = taskUrl.parameters["end"]?.toIntOrNull() ?: 65536
        val shouldTranslateExpiredChapter = taskUrl.parameters["expire"]?.toBoolean() ?: false

        this.description = job.description + "\n" + "${novelId}/${volumeId}"

        logger.info("获取小说和卷")
        val novel = wenkuMetadataRepo.get(novelId)
            ?: return
        val filteredGlossary = novel
            .glossary
            .filterKeys { it.length >= 3 }

        val volume = wenkuVolumeRepo.getVolume(novelId, volumeId)
            ?: return

        logger.info("构造需要翻译的章节列表")
        val chapters = volume
            .listChapter()
            .filterIndexed { index, _ -> index in start..<end }
            .filter {
                if (!volume.translationExist(TranslatorId.Sakura, it)) {
                    // 未翻译
                    true
                } else if (
                    volume.getChapterGlossary(TranslatorId.Sakura, it)?.uuid == novel?.glossaryUuid
                ) {
                    // 翻译未过期
                    false
                } else {
                    // 翻译已过期
                    shouldTranslateExpiredChapter
                }
            }

        logger.info("开始翻译")
        val total = chapters.size
        var finished = 0

        fun updateProgress() {
            this.progress = SakuraWorkerProgress(
                total = total,
                finished = finished,
            )
        }

        chapters.forEach { chapterId ->
            updateProgress()
            this.description = job.description + "\n" + "${novelId}/${volumeId}/${chapterId}"

            logger.info("获取章节 ${novelId}/${volumeId}/${chapterId}")
            val paragraphs = volume
                .getChapter(chapterId)!!
                .map {
                    var line = it
                    filteredGlossary.forEach { (jpWord, zhWord) ->
                        line = line.replace(jpWord, zhWord)
                    }
                    line
                }

            logger.info("翻译章节 ${novelId}/${volumeId}/${chapterId}")
            val sakuraParagraphs = sakuraTranslate(
                client = client,
                endpoint = endpoint,
                input = paragraphs,
            ) { prompt, result ->
                mongo
                    .sakuraWenkuFailCaseCollection
                    .insertOne(
                        SakuraWenkuFailCase(
                            novelId = novelId,
                            volumeId = volumeId,
                            chapterId = chapterId,
                            prompt = prompt,
                            result = result,
                        )
                    )
            }

            logger.info("更新章节 ${novelId}/${volumeId}/${chapterId}")
            volume.setTranslation(
                translatorId = TranslatorId.Sakura,
                chapterId = chapterId,
                lines = sakuraParagraphs,
            )
            volume.setChapterGlossary(
                translatorId = TranslatorId.Sakura,
                chapterId = chapterId,
                glossaryUuid = novel.glossaryUuid,
                glossary = novel.glossary,
                sakuraVersion = "0.9",
            )

            finished += 1
        }
    }
}