package infra

import domain.entity.*
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
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.*

sealed class VolumeCreateException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class VolumeAlreadyExist : VolumeCreateException("卷已经存在")
    class VolumeUnpackFailure(cause: Throwable) : VolumeCreateException("卷解包失败", cause)
}

@OptIn(ExperimentalPathApi::class)
class DataSourceFileSystem {
    suspend fun listVolumes(
        volumesDir: Path,
    ) = withContext(Dispatchers.IO) {
        val volumesJp = mutableListOf<WenkuNovelVolumeJp>()
        val volumesZh = mutableListOf<String>()
        if (volumesDir.exists() && volumesDir.isDirectory()) {
            volumesDir
                .listDirectoryEntries()
                .filter {
                    it.isRegularFile() && it.fileName.extension in listOf("epub", "txt")
                }
                .map {
                    VolumeAccessor(volumesDir, it.fileName.toString())
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
                                sakura = it.listTranslation(TranslatorId.Sakura).size,
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

    suspend fun createVolume(
        volumesDir: Path,
        volumeId: String,
        inputStream: InputStream,
        unpack: Boolean,
    ) = withContext(Dispatchers.IO) {
        if (!volumesDir.exists()) {
            volumesDir.createDirectories()
        }

        val volumePath = volumesDir / volumeId
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
                unpackVolume(volumesDir, volumeId)
            } catch (e: Throwable) {
                e.printStackTrace()
                val unpackPath = volumesDir / "$volumeId.unpack"
                volumePath.deleteIfExists()
                unpackPath.deleteRecursively()
                throw VolumeCreateException.VolumeUnpackFailure(e)
            }
        }
    }

    private suspend fun unpackVolume(
        volumesDir: Path,
        volumeId: String,
    ) = withContext(Dispatchers.IO) {
        val volumePath = volumesDir / volumeId
        val unpackPath = volumesDir / "$volumeId.unpack" / "jp"
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

    suspend fun deleteVolume(
        volumesDir: Path,
        volumeId: String,
    ) = withContext(Dispatchers.IO) {
        val trashDir = volumesDir.parent / "trash"

        if (trashDir.notExists()) {
            trashDir.createDirectories()
        }

        val volumePath = volumesDir / volumeId
        val unpackPath = volumesDir / "$volumeId.unpack"

        val volumeTrashPath = trashDir / "${volumesDir.fileName}.${volumeId}"
        val unpackTrashPath = trashDir / "${volumesDir.fileName}.${volumeId}.unpack"

        if (volumePath.exists()) {
            volumePath.moveTo(volumeTrashPath)
        }
        if (unpackPath.exists()) {
            unpackPath.moveTo(unpackTrashPath)
        }
    }

    suspend fun getVolume(
        volumesDir: Path,
        volumeId: String,
    ): VolumeAccessor? = withContext(Dispatchers.IO) {
        val volumePath = volumesDir / volumeId
        val unpackPath = volumesDir / "$volumeId.unpack"
        return@withContext if (volumePath.exists() && unpackPath.exists()) {
            VolumeAccessor(volumesDir, volumeId)
        } else {
            null
        }
    }
}

private fun String.escapePath() =
    replace('/', '.')

class VolumeAccessor(private val volumesDir: Path, val volumeId: String) {
    val unpacked
        get() = (volumesDir / "${volumeId}.unpack").exists()

    //
    private suspend fun listFiles(dir: String) =
        withContext(Dispatchers.IO) {
            val chapterPath = volumesDir / "$volumeId.unpack" / dir
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


    private fun Path.readLinesOrNull() =
        if (notExists()) null else readLines()

    //
    private fun chapterPath(chapterId: String) =
        volumesDir / "$volumeId.unpack" / "jp" / chapterId

    suspend fun getChapter(chapterId: String) =
        withContext(Dispatchers.IO) {
            val path = chapterPath(chapterId)
            return@withContext path.readLinesOrNull()
        }

    //
    private fun translationPath(translatorId: TranslatorId, chapterId: String) =
        volumesDir / "$volumeId.unpack" / translatorId.serialName() / chapterId

    suspend fun translationExist(translatorId: TranslatorId, chapterId: String) =
        withContext(Dispatchers.IO) {
            val path = translationPath(translatorId, chapterId)
            return@withContext path.exists()
        }

    private suspend fun getTranslation(translatorId: TranslatorId, chapterId: String) =
        withContext(Dispatchers.IO) {
            val path = translationPath(translatorId, chapterId)
            return@withContext path.readLinesOrNull()
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
    private fun chapterGlossaryPath(translatorId: TranslatorId, chapterId: String) =
        volumesDir / "$volumeId.unpack" / "${translatorId.serialName()}.g" / chapterId

    suspend fun getChapterGlossary(
        translatorId: TranslatorId,
        chapterId: String,
    ) = getGlossary(
        path = chapterGlossaryPath(
            translatorId = translatorId,
            chapterId = chapterId,
        ),
    )

    suspend fun setChapterGlossary(
        translatorId: TranslatorId,
        chapterId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        sakuraVersion: String?,
    ) = setGlossary(
        path = chapterGlossaryPath(
            translatorId = translatorId,
            chapterId = chapterId,
        ),
        glossaryUuid = glossaryUuid,
        glossary = glossary,
        sakuraVersion = sakuraVersion,
    )

    //
    suspend fun makeTranslationVolumeFile(
        mode: NovelFileMode,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
    ) = withContext(Dispatchers.IO) {
        val zhFilename = buildString {
            append(mode.serialName())
            append('.')
            append(
                when (translationsMode) {
                    NovelFileTranslationsMode.Parallel -> "B"
                    NovelFileTranslationsMode.Priority -> "Y"
                }
            )
            translations.forEach {
                append(it.serialName()[0])
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
            val zhPath = volumesDir / "$volumeId.unpack" / "${zhFilename}.txt"

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
                        val linesList = when (mode) {
                            NovelFileMode.Jp -> throw RuntimeException("文库小说不允许日语下载")
                            NovelFileMode.Zh -> zhLinesList
                            NovelFileMode.JpZh -> listOf(jpLines) + zhLinesList
                            NovelFileMode.ZhJp -> zhLinesList + listOf(jpLines)
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
            val zhPath = volumesDir / "$volumeId.unpack" / "${zhFilename}.epub"
            val jpPath = volumesDir / volumeId

            val chapters = listChapter()
            Epub.modify(srcPath = jpPath, dstPath = zhPath) { name, bytesIn ->
                // 为了兼容ChapterId以斜杠开头的旧格式
                val chapterId = if ("/${name}".escapePath() in chapters) {
                    "/${name}".escapePath()
                } else if (name.escapePath() in chapters) {
                    name.escapePath()
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
                                when (mode) {
                                    NovelFileMode.Jp -> throw RuntimeException("文库小说不允许日语下载")
                                    NovelFileMode.Zh -> {
                                        zhLinesList.forEach { lines ->
                                            el.before("<p>${lines[index]}</p>")
                                        }
                                        el.remove()
                                    }

                                    NovelFileMode.JpZh -> {
                                        zhLinesList.asReversed().forEach { lines ->
                                            el.after("<p>${lines[index]}</p>")
                                        }
                                        el.attr("style", "opacity:0.4;")
                                    }

                                    NovelFileMode.ZhJp -> {
                                        zhLinesList.forEach { lines ->
                                            el.before("<p>${lines[index]}</p>")
                                        }
                                        el.attr("style", "opacity:0.4;")
                                    }
                                }
                            }
                        doc.outputSettings().prettyPrint(true)
                        doc.html().toByteArray()
                    }
                } else if (name.endsWith("opf")) {
                    val doc = Jsoup.parse(bytesIn.decodeToString(), Parser.xmlParser())

                    // 防止部分阅读器使用竖排
                    doc
                        .selectFirst("spine")
                        ?.removeAttr("page-progression-direction")

                    doc.outputSettings().prettyPrint(true)
                    doc.html().toByteArray()
                } else if (name.endsWith("css")) {
                    "".toByteArray()
                } else {
                    bytesIn
                }
            }
            return@withContext "${zhFilename}.epub"
        }
    }
}

private suspend fun getGlossary(path: Path) =
    withContext(Dispatchers.IO) {
        return@withContext if (path.notExists())
            null
        else try {
            Json.decodeFromString<WenkuChapterGlossary>(path.readText())
        } catch (e: Throwable) {
            null
        }
    }

private suspend fun setGlossary(
    path: Path,
    glossaryUuid: String?,
    glossary: Map<String, String>,
    sakuraVersion: String?,
) = withContext(Dispatchers.IO) {
    if (path.parent.notExists()) {
        path.parent.createDirectories()
    }
    path.writeText(
        Json.encodeToString(
            WenkuChapterGlossary(
                uuid = glossaryUuid,
                glossary = glossary,
                sakuraVersion = sakuraVersion
            )
        )
    )
}

