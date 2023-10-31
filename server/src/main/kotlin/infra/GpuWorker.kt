package infra

import infra.model.*
import infra.web.WebNovelChapterRepository
import infra.web.providers.json
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bson.types.ObjectId
import org.litote.kmongo.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GpuWorkerManager(
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

    private val _workers = mutableMapOf<String, GpuWorker>()
    val workers: Map<String, GpuWorker>
        get() = _workers

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            delay(10.seconds.toJavaDuration())
            val cards = mongo
                .gpuCardCollection
                .find()
                .toList()
            cards.forEach {
                val worker = GpuWorker(
                    scope = scope,
                    card = it,
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
        val card = GpuCard(
            id = ObjectId(),
            gpu = gpu,
            endpoint = endpoint,
        )
        val id = mongo
            .gpuCardCollection
            .insertOne(card)
            .insertedId!!
            .asObjectId().value

        val worker = GpuWorker(
            scope = scope,
            card = card.copy(id = id),
            client = client,
            mongo = mongo,
            chapterRepo = webNovelChapterRepo,
        )
        _workers[worker.id] = worker
    }

    fun startWorker(id: String) {
        _workers[id]?.start()
    }

    suspend fun stopWorker(id: String) {
        _workers[id]?.stop()
    }

    suspend fun deleteWorker(id: String) {
        mongo
            .gpuCardCollection
            .deleteOneById(id)
        _workers[id]?.stop()
        _workers.remove(id)
    }
}

@Serializable
data class GpuWorkerProgress(
    val total: Int,
    val finished: Int,
    val error: Int,
)

class GpuWorker(
    private val scope: CoroutineScope,
    private val card: GpuCard,
    private val client: HttpClient,
    private val mongo: DataSourceMongo,
    private val chapterRepo: WebNovelChapterRepository,
) {
    private var job = createRunningJob()

    val id
        get() = card.id.toHexString()
    val gpu
        get() = card.gpu
    val isActive
        get() = job.isActive

    var description: String = ""
        private set

    var progress: GpuWorkerProgress? = null
        private set

    fun start() {
        if (isActive) return
        job = createRunningJob()
    }

    suspend fun stop() {
        if (!isActive) return
        job.cancelAndJoin()
        releaseGpuJob()
    }

    private fun createRunningJob(): Job {
        val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
            scope.launch { releaseGpuJob() }
            println("Gpu worker error: $card")
            throwable.printStackTrace()
        }
        return scope.launch(coroutineExceptionHandler) {
            run()
        }
    }

    private suspend fun releaseGpuJob() {
        mongo
            .gpuJobCollection
            .updateMany(
                GpuJob::workerId eq id,
                setValue(GpuJob::workerId, null),
            )
    }

    private suspend fun run() {
        while (true) {
            val job = mongo
                .gpuJobCollection
                .findOne(GpuJob::workerId eq id)
                ?: mongo
                    .gpuJobCollection
                    .findOneAndUpdate(
                        GpuJob::workerId eq null,
                        setValue(GpuJob::workerId, id),
                    )
            if (job == null) {
                delay(10.seconds.toJavaDuration())
                continue
            }
            try {
                processJob(job)

                mongo
                    .gpuJobCollection
                    .deleteOne(GpuJob::id eq job.id)

                mongo
                    .gpuJobResultCollection
                    .insertOne(
                        GpuJobResult(
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
                    .gpuJobCollection
                    .updateMany(
                        GpuJob::workerId eq id,
                        setValue(GpuJob::workerId, null),
                    )
                if (e is CancellationException) throw e
            } finally {
                progress = null
                description = ""
            }
        }
    }

    private suspend fun processJob(job: GpuJob) {
        this.description = job.task + "\n" + job.description

        val taskUrl = URLBuilder().takeFrom(job.task).build()
        when (taskUrl.pathSegments.first()) {
            "web" -> processWebTranslateJob(taskUrl)
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
                it.subList(0, it.size.coerceAtMost(50))
            }

        val total = untranslatedChapterId.size
        var finished = 0
        var error = 0

        fun updateProgress() {
            this.progress = GpuWorkerProgress(
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

                val sakuraParagraphs = translateByGpu(chapter.paragraphs) { prompt, result ->
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
                error += 1
                updateProgress()
            }
        }
    }

    private suspend fun translateByGpu(
        input: List<String>,
        onSakuraFail: suspend (prompt: String, result: String) -> Unit,
    ): List<String> {
        fun splitByLength(paragraphs: List<String>, limit: Int): List<List<String>> {
            val segs = mutableListOf<List<String>>()
            var seg = mutableListOf<String>()
            var total = 0
            paragraphs.forEach {
                if (total + it.length > limit) {
                    segs.add(seg)
                    seg = mutableListOf(it)
                    total = it.length
                } else {
                    seg.add(it)
                    total += it.length
                }
            }
            if (seg.isNotEmpty()) {
                segs.add(seg)
            }
            return segs
        }

        val filteredInput = filterInput(input)
        val output = splitByLength(filteredInput, 500)
            .flatMap { seg -> translateSegByGpu(seg, onSakuraFail) }
        val recoveredOutput = recoverOutput(input, output)
        if (recoveredOutput.size != input.size) {
            throw RuntimeException("重建翻译长度不匹配，不应当出现");
        }
        return recoveredOutput
    }

    private suspend fun translateSegByGpu(
        seg: List<String>,
        onSakuraFail: suspend (prompt: String, result: String) -> Unit,
    ): List<String> {
        @Serializable
        data class ParamBody(
            val prompt: String,
            val preset: String,
            val max_new_tokens: Int,

            val do_sample: Boolean,
            val temperature: Double,
            val top_p: Double,
            val top_k: Int,
            val num_beams: Int,
            val repetition_penalty: Double,
        )

        var retry = 0

        while (retry < 4) {
            val prompt = "<reserved_106>将下面的日文文本翻译成中文：${seg.joinToString("\n")}<reserved_107>";
            val body = if (retry == 0) {
                ParamBody(
                    prompt = prompt,
                    preset = "None",
                    max_new_tokens = 1024,
                    do_sample = true,
                    temperature = 0.1,
                    top_p = 0.3,
                    top_k = 40,
                    num_beams = 1,
                    repetition_penalty = 1.0,
                )
            } else {
                ParamBody(
                    prompt = prompt,
                    preset = "None",
                    max_new_tokens = 1024,
                    do_sample = true,
                    temperature = 1.0,
                    top_p = 0.5,
                    top_k = 40,
                    num_beams = 1,
                    repetition_penalty = 1.1,
                )
            }
            val obj = client.post(card.endpoint) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.json()
            val result = obj["results"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
            val splitResult = result.split("\n")

            if (splitResult.size == seg.size) {
                return splitResult
            } else {
                if (retry == 0) {
                    // 第一次失败时记录数据
                    onSakuraFail(prompt, result)
                }
                retry += 1
            }
        }
        throw RuntimeException("行数不匹配")
    }
}

private fun filterInput(input: List<String>): List<String> {
    return input.map {
        it
            .replace("/\r?\n|\r/g".toRegex(), "")
            .replace("　", " ")
            .trim()
    }.filterNot {
        it.isBlank() || it.startsWith("<图片>")
    }
}

private fun recoverOutput(input: List<String>, output: List<String>): List<String> {
    val mutableOutput = output.toMutableList()
    val recoveredOutput = mutableListOf<String>()
    input.forEach {
        val realLine = it
            .replace("/\r?\n|\r/g".toRegex(), "")
            .replace("　", " ")
            .trim()
        if (realLine.isBlank() || realLine.startsWith("<图片>")) {
            recoveredOutput.add(it);
        } else {
            recoveredOutput.add(
                mutableOutput.removeFirst()
            )
        }
    }
    return recoveredOutput;
}