package infra

import infra.model.GpuJob
import infra.model.GpuJobResult
import infra.model.SakuraFailCase
import infra.model.WebNovelChapter
import infra.web.WebNovelChapterRepository
import infra.web.providers.json
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.litote.kmongo.and
import org.litote.kmongo.eq
import org.litote.kmongo.ne
import org.litote.kmongo.setValue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GpuWorkerManager(
    mongo: DataSourceMongo,
    webNovelChapterRepo: WebNovelChapterRepository,
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

    val workers = listOf(
        GpuWorker(
            id = "20537575-b0b4-4593-9666-e1b332deadce",
            card = "RTX 3090",
            endpoint = "http://192.168.1.162:5000/api/v1/generate",
            client = client,
            mongo = mongo,
            chapterRepo = webNovelChapterRepo,
        )
    )
}

@Serializable
data class GpuWorkerProgress(
    val total: Int,
    val finished: Int,
    val error: Int,
)

class GpuWorker(
    val id: String,
    val card: String,
    private val endpoint: String,
    private val client: HttpClient,
    private val mongo: DataSourceMongo,
    private val chapterRepo: WebNovelChapterRepository,
) {
    val job = CoroutineScope(Dispatchers.Default).launch {
        delay(10.seconds.toJavaDuration())
        run()
    }

    var description: String = ""
        private set

    var progress: GpuWorkerProgress? = null
        private set

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
                delay(5.seconds.toJavaDuration())
            } else {
                processJob(job)
            }
        }
    }

    private suspend fun processJob(job: GpuJob) {
        this.description = job.task + "\n" + job.description
        when {
            job.task.startsWith("web") -> {
                val (_, providerId, novelId) = job.task.split('/')
                processWebTranslateJob(providerId, novelId)
            }
        }

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

        progress = null
        description = ""
    }

    private suspend fun processWebTranslateJob(
        providerId: String,
        novelId: String,
    ) {
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

        while (retry < 3) {
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
            val obj = client.post(endpoint) {
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