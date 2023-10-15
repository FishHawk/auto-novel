package infra.wenku

import infra.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import util.epub.Epub
import util.serialName
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.*

private fun String.escapePath() =
    replace('/', '.')

sealed class VolumeCreateException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class VolumeAlreadyExist : VolumeCreateException("卷已经存在")
    class VolumeUnpackFailure(cause: Throwable) : VolumeCreateException("卷解包失败", cause)
}

class WenkuNovelVolumeRepository {
    private val root = Path("./data/files-wenku")

    suspend fun list(
        novelId: String,
    ): WenkuNovelVolumeList = withContext(Dispatchers.IO) {
        val novelPath = root / novelId

        val volumesJp = mutableListOf<WenkuNovelVolumeJp>()
        val volumesZh = mutableListOf<String>()
        if (novelPath.exists() && novelPath.isDirectory()) {
            novelPath
                .listDirectoryEntries()
                .filter {
                    it.isRegularFile() && it.fileName.extension in listOf("epub", "txt")
                }
                .map {
                    VolumeAccessor(novelPath, it.fileName.toString())
                }
                .forEach {
                    if (it.unpacked) {
                        volumesJp.add(
                            WenkuNovelVolumeJp(
                                volumeId = it.volumeId,
                                total = it.listChapter().size,
                                baidu = it.listTranslation(TranslatorId.Baidu).size,
                                youdao = it.listTranslation(TranslatorId.Youdao).size,
                                gpt = it.listTranslation(TranslatorId.Gpt).size,
                            )
                        )
                    } else {
                        volumesZh.add(it.volumeId)
                    }
                }
        }
        return@withContext WenkuNovelVolumeList(
            jp = volumesJp,
            zh = volumesZh,
        )
    }

    @OptIn(ExperimentalPathApi::class)
    suspend fun createVolume(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
        unpack: Boolean,
    ) = withContext(Dispatchers.IO) {
        val novelPath = root / novelId
        if (!novelPath.exists()) {
            novelPath.createDirectories()
        }

        val volumePath = novelPath / volumeId
        val volumeOutputStream = try {
            volumePath.createFile().outputStream()
        } catch (e: FileAlreadyExistsException) {
            throw VolumeCreateException.VolumeAlreadyExist()
        }
        val volumeTooLarge = volumeOutputStream.use { out ->
            var bytesCopied: Long = 0
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                bytesCopied += bytes
                if (bytesCopied > 1024 * 1024 * 40) {
                    return@use true
                }
                out.write(buffer, 0, bytes)
                bytes = inputStream.read(buffer)
            }
            return@use false
        }

        if (volumeTooLarge) {
            volumePath.deleteIfExists()
            throw RuntimeException("文件大小不能超过40MB")
        }

        if (unpack) {
            try {
                unpackVolume(novelId, volumeId)
            } catch (e: Throwable) {
                e.printStackTrace()
                val unpackPath = novelPath / "$volumeId.unpack"
                volumePath.deleteIfExists()
                unpackPath.deleteRecursively()
                throw VolumeCreateException.VolumeUnpackFailure(e)
            }
        }
    }

    private suspend fun unpackVolume(
        novelId: String,
        volumeId: String,
    ): Unit = withContext(Dispatchers.IO) {
        val volumePath = root / novelId / volumeId
        val unpackPath = root / novelId / "$volumeId.unpack" / "jp"
        if (unpackPath.notExists()) {
            unpackPath.createDirectories()
        }
        if (volumePath.extension == "txt") {
            val jpLines = runCatching {
                volumePath.readLines()
            }.getOrElse {
                volumePath.readLines(Charset.forName("GBK"))
            }
            jpLines.chunked(1000).forEachIndexed { index, lines ->
                val chapterPath = unpackPath / "${String.format("%04d", index)}.txt"
                chapterPath.writeLines(lines)
            }
        } else {
            Epub.forEachXHtmlFile(volumePath) { xhtmlPath, doc ->
                doc.select("rt").remove()
                val lines = doc.body().select("p")
                    .mapNotNull { it.text().ifBlank { null } }
                if (lines.isNotEmpty()) {
                    val chapterPath = unpackPath / xhtmlPath.escapePath()
                    chapterPath.writeLines(lines)
                }
            }
        }
    }

    suspend fun getVolumeJp(
        novelId: String,
        volumeId: String,
    ) = withContext(Dispatchers.IO) {
        val volumePath = root / novelId / volumeId
        val unpackPath = root / novelId / "$volumeId.unpack"
        return@withContext if (volumePath.exists() && unpackPath.exists()) {
            VolumeAccessor(root / novelId, volumeId)
        } else {
            null
        }
    }
}

class VolumeAccessor(private val novelPath: Path, val volumeId: String) {
    val unpacked
        get() = (novelPath / "${volumeId}.unpack").exists()

    //
    private suspend fun listFiles(dir: String) =
        withContext(Dispatchers.IO) {
            val chapterPath = novelPath / "$volumeId.unpack" / dir
            return@withContext if (chapterPath.notExists()) {
                emptyList()
            } else {
                chapterPath
                    .listDirectoryEntries()
                    .map { it.fileName.toString() }
            }
        }

    suspend fun listChapter(): List<String> =
        listFiles("jp")

    suspend fun listTranslation(translatorId: TranslatorId): List<String> =
        listFiles(translatorId.serialName())


    private fun Path.readTextOrNull() =
        if (notExists()) null else readText()

    //
    private fun chapterPath(chapterId: String) =
        novelPath / "$volumeId.unpack" / "jp" / chapterId

    suspend fun getChapter(chapterId: String) =
        withContext(Dispatchers.IO) {
            val path = chapterPath(chapterId)
            return@withContext path.readTextOrNull()?.lines()
        }

    //
    private fun translationPath(translatorId: TranslatorId, chapterId: String) =
        novelPath / "$volumeId.unpack" / translatorId.serialName() / chapterId

    suspend fun translationExist(translatorId: TranslatorId, chapterId: String) =
        withContext(Dispatchers.IO) {
            val path = translationPath(translatorId, chapterId)
            return@withContext path.exists()
        }

    private suspend fun getTranslation(translatorId: TranslatorId, chapterId: String) =
        withContext(Dispatchers.IO) {
            val path = translationPath(translatorId, chapterId)
            return@withContext path.readTextOrNull()?.lines()
        }

    suspend fun setTranslation(translatorId: TranslatorId, chapterId: String, lines: List<String>) =
        withContext(Dispatchers.IO) {
            val path = translationPath(translatorId, chapterId)
            if (path.parent.notExists()) {
                path.parent.createDirectories()
            }
            path.writeLines(lines)
        }

    //
    private fun glossaryPath(translatorId: TranslatorId, chapterId: String) =
        novelPath / "$volumeId.unpack" / "${translatorId.serialName()}.g" / chapterId

    suspend fun getChapterGlossary(translatorId: TranslatorId, chapterId: String) =
        withContext(Dispatchers.IO) {
            val path = glossaryPath(translatorId, chapterId)
            return@withContext if (path.notExists())
                null
            else try {
                Json.decodeFromString<WenkuChapterGlossary>(path.readText())
            } catch (e: Throwable) {
                null
            }
        }

    suspend fun setChapterGlossary(
        translatorId: TranslatorId,
        chapterId: String,
        glossaryUuid: String,
        glossary: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        val path = glossaryPath(translatorId, chapterId)
        if (path.parent.notExists()) {
            path.parent.createDirectories()
        }
        path.writeText(
            Json.encodeToString(WenkuChapterGlossary(glossaryUuid, glossary))
        )
    }

    //
    suspend fun makeTranslationVolumeFile(
        lang: NovelFileLangV2,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
    ) = withContext(Dispatchers.IO) {
        val zhFilename = buildString {
            append(
                when (lang) {
                    NovelFileLangV2.Jp -> "jp"
                    NovelFileLangV2.Zh -> "zh"
                    NovelFileLangV2.JpZh -> "jp-zh"
                    NovelFileLangV2.ZhJp -> "zh-jp"
                }
            )
            append('.')
            append(
                when (translationsMode) {
                    NovelFileTranslationsMode.Parallel -> "B"
                    NovelFileTranslationsMode.Priority -> "Y"
                }
            )
            translations.forEach {
                append(
                    when (it) {
                        TranslatorId.Baidu -> "b"
                        TranslatorId.Youdao -> "y"
                        TranslatorId.Gpt -> "g"
                    }
                )
            }
        }

        suspend fun getZhLinesList(chapterId: String): List<List<String>> {
            return when (translationsMode) {
                NovelFileTranslationsMode.Parallel ->
                    translations.mapNotNull { getTranslation(it, chapterId) }

                NovelFileTranslationsMode.Priority ->
                    translations.firstNotNullOfOrNull { getTranslation(it, chapterId) }
                        ?.let { listOf(it) }
                        ?: emptyList()
            }
        }

        if (volumeId.endsWith(".txt")) {
            val zhPath = novelPath / "$volumeId.unpack" / "${zhFilename}.txt"

            if (zhPath.notExists()) {
                zhPath.createFile()
            }

            zhPath.bufferedWriter().use { bf ->
                listChapter().sorted().forEach { chapterId ->
                    val zhLinesList = getZhLinesList(chapterId)
                    if (zhLinesList.isEmpty()) {
                        bf.appendLine("// 该分段翻译缺失。")
                    } else {
                        val jpLines = getChapter(chapterId)!!
                        val linesList = when (lang) {
                            NovelFileLangV2.Jp -> throw RuntimeException("文库小说不允许日语下载")
                            NovelFileLangV2.Zh -> zhLinesList
                            NovelFileLangV2.JpZh -> listOf(jpLines) + zhLinesList
                            NovelFileLangV2.ZhJp -> zhLinesList + listOf(jpLines)
                        }
                        for (i in jpLines.indices) {
                            linesList.forEach { lines ->
                                bf.appendLine(lines[i])
                            }
                        }
                    }
                }
            }
            return@withContext "${zhFilename}.txt"
        } else {
            val zhPath = novelPath / "$volumeId.unpack" / "${zhFilename}.epub"
            val jpPath = novelPath / volumeId

            val chapters = listChapter()
            Epub.modify(srcPath = jpPath, dstPath = zhPath) { entry, bytesIn ->
                // 为了兼容ChapterId以斜杠开头的旧格式
                val chapterId = if ("/${entry.name}".escapePath() in chapters) {
                    "/${entry.name}".escapePath()
                } else if (entry.name.escapePath() in chapters) {
                    entry.name.escapePath()
                } else {
                    null
                }

                if (chapterId != null) {
                    // XHtml文件，尝试生成翻译版
                    val zhLinesList = getZhLinesList(chapterId)
                    if (zhLinesList.isEmpty()) {
                        bytesIn
                    } else {
                        val doc = Jsoup.parse(bytesIn.decodeToString(), Parser.xmlParser())
                        doc.select("p")
                            .filter { el -> el.text().isNotBlank() }
                            .forEachIndexed { index, el ->
                                when (lang) {
                                    NovelFileLangV2.Jp -> throw RuntimeException("文库小说不允许日语下载")
                                    NovelFileLangV2.Zh -> {
                                        zhLinesList.forEach { lines ->
                                            el.before("<p>${lines[index]}<p>")
                                        }
                                        el.remove()
                                    }

                                    NovelFileLangV2.JpZh -> {
                                        zhLinesList.asReversed().forEach { lines ->
                                            el.after("<p>${lines[index]}<p>")
                                        }
                                        el.attr("style", "opacity:0.4;")
                                    }

                                    NovelFileLangV2.ZhJp -> {
                                        zhLinesList.forEach { lines ->
                                            el.before("<p>${lines[index]}<p>")
                                        }
                                        el.attr("style", "opacity:0.4;")
                                    }
                                }
                            }
                        doc.outputSettings().prettyPrint(true)
                        doc.html().toByteArray()
                    }
                } else if (entry.name.endsWith("css")) {
                    "".toByteArray()
                } else {
                    bytesIn
                }
            }
            return@withContext "${zhFilename}.epub"
        }
    }
}
