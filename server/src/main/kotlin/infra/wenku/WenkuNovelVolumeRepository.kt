package infra.wenku

import infra.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import util.epub.Epub
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.io.path.*

@Serializable
data class ChapterGlossary(
    val uuid: String,
    val glossary: Map<String, String>,
)

private fun String.escapePath() = replace('/', '.')

class WenkuNovelVolumeRepository {
    private val root = Path("./data/files-wenku")

    suspend fun listVolumesNonArchived() = list("non-archived").jp
    suspend fun listVolumesUser(userId: String) = list(userId).jp

    suspend fun list(
        novelId: String,
    ): WenkuNovelVolumeList = withContext(Dispatchers.IO) {
        val path = root / novelId
        val fileExtensions = listOf("epub", "txt")

        val volumesJp = mutableListOf<WenkuNovelVolumeJp>()
        val volumesZh = mutableListOf<String>()
        if (path.exists() && path.isDirectory()) {
            path.listDirectoryEntries()
                .sortedBy { it.fileName }
                .filter { it.isRegularFile() && it.fileName.extension in fileExtensions }
                .forEach {
                    val volumeId = it.fileName.toString()
                    if (hasUnpacked(novelId, volumeId)) {
                        volumesJp.add(
                            WenkuNovelVolumeJp(
                                volumeId = volumeId,
                                total = listUnpackedChapters(novelId, volumeId, "jp").size.toLong(),
                                baidu = listUnpackedChapters(novelId, volumeId, "baidu").size.toLong(),
                                youdao = listUnpackedChapters(novelId, volumeId, "youdao").size.toLong(),
                                gpt = listUnpackedChapters(novelId, volumeId, "gpt").size.toLong()
                            )
                        )
                    } else {
                        volumesZh.add(volumeId)
                    }
                }
        }
        return@withContext WenkuNovelVolumeList(jp = volumesJp, zh = volumesZh)
    }


    private fun hasUnpacked(novelId: String, volumeId: String): Boolean {
        val unpackPath = root / novelId / "$volumeId.unpack"
        return unpackPath.exists()
    }


    suspend fun getTranslatedNumber(
        novelId: String,
        volumeId: String,
        translatorId: TranslatorId,
    ): Long {
        val type = when (translatorId) {
            TranslatorId.Baidu -> "baidu"
            TranslatorId.Youdao -> "youdao"
            TranslatorId.Gpt -> "gpt"
        }
        return listUnpackedChapters(novelId, volumeId, type).size.toLong()
    }

    suspend fun listUnpackedChapters(
        novelId: String,
        volumeId: String,
        type: String,
    ) = withContext(Dispatchers.IO) {
        val unpackPath = root / novelId / "$volumeId.unpack" / type
        return@withContext if (unpackPath.notExists()) {
            emptyList()
        } else {
            unpackPath
                .listDirectoryEntries()
                .map { it.fileName.toString() }
        }
    }

    suspend fun isVolumeJpExisted(
        novelId: String,
        volumeId: String,
    ) = withContext(Dispatchers.IO) {
        val volumePath = root / novelId / volumeId
        val unpackPath = root / novelId / "$volumeId.unpack"
        return@withContext volumePath.exists() && unpackPath.exists()
    }

    suspend fun createVolumeAndOpen(
        novelId: String,
        volumeId: String,
    ): Result<OutputStream> = withContext(Dispatchers.IO) {
        val novelPath = root / novelId
        if (!novelPath.exists()) {
            novelPath.createDirectories()
        }
        val filePath = novelPath / volumeId
        return@withContext if (filePath.exists()) {
            Result.failure(RuntimeException("文件已存在"))
        } else {
            Result.success(filePath.createFile().outputStream())
        }
    }

    suspend fun deleteVolumeIfExist(
        novelId: String,
        volumeId: String,
    ) = withContext(Dispatchers.IO) {
        val filePath = root / novelId / volumeId
        filePath.deleteIfExists()
    }

    suspend fun deleteVolumeJpIfExist(
        novelId: String,
        volumeId: String,
    ) = withContext(Dispatchers.IO) {
        val filePath = root / novelId / volumeId
        val unpackPath = root / novelId / "$volumeId.unpack"
        filePath.moveTo(root / novelId / "deleted" / volumeId)
        unpackPath.deleteIfExists()
    }

    private fun isTxt(volumeId: String): Boolean {
        return volumeId.lowercase().endsWith(".txt")
    }

    suspend fun unpackVolume(
        novelId: String,
        volumeId: String,
    ): Unit = withContext(Dispatchers.IO) {
        val filePath = root / novelId / volumeId
        val unpackPath = root / novelId / "$volumeId.unpack" / "jp"
        if (unpackPath.notExists()) {
            unpackPath.createDirectories()
        }
        if (isTxt(volumeId)) {
            val jpLines = runCatching {
                filePath.readLines()
            }.getOrElse {
                filePath.readLines(Charset.forName("GBK"))
            }
            jpLines.chunked(1000).forEachIndexed { index, lines ->
                val chapterPath = unpackPath / "${String.format("%04d", index)}.txt"
                chapterPath.writeLines(lines)
            }
        } else {
            Epub.forEachXHtmlFile(filePath) { xhtmlPath, doc ->
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

    private suspend fun getChapter(
        novelId: String,
        volumeId: String,
        type: String,
        chapterId: String,
    ) = withContext(Dispatchers.IO) {
        val path = root / novelId / "$volumeId.unpack" / type / chapterId
        return@withContext if (path.notExists()) null
        else path.readText().lines()
    }

    suspend fun getChapter(
        novelId: String,
        volumeId: String,
        chapterId: String,
    ) = getChapter(novelId, volumeId, "jp", chapterId)

    suspend fun getChapterGlossary(
        novelId: String,
        volumeId: String,
        translatorId: TranslatorId,
        chapterId: String,
    ) = withContext(Dispatchers.IO) {
        val type = when (translatorId) {
            TranslatorId.Baidu -> "baidu.g"
            TranslatorId.Youdao -> "youdao.g"
            TranslatorId.Gpt -> "gpt.g"
        }
        val path = root / novelId / "$volumeId.unpack" / type / chapterId
        return@withContext if (path.notExists()) null
        else try {
            Json.decodeFromString<ChapterGlossary>(path.readText())
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun isTranslationExist(
        novelId: String,
        volumeId: String,
        translatorId: TranslatorId,
        chapterId: String,
    ) = withContext(Dispatchers.IO) {
        val type = when (translatorId) {
            TranslatorId.Baidu -> "baidu"
            TranslatorId.Youdao -> "youdao"
            TranslatorId.Gpt -> "gpt"
        }
        val path = root / novelId / "$volumeId.unpack" / type / chapterId
        return@withContext path.exists()
    }

    suspend fun updateTranslation(
        novelId: String,
        volumeId: String,
        translatorId: TranslatorId,
        chapterId: String,
        lines: List<String>,
        glossaryUuid: String?,
        glossary: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        val type = when (translatorId) {
            TranslatorId.Baidu -> "baidu"
            TranslatorId.Youdao -> "youdao"
            TranslatorId.Gpt -> "gpt"
        }
        val path = root / novelId / "$volumeId.unpack" / type / chapterId
        if (path.parent.notExists()) {
            path.parent.createDirectories()
        }
        if (path.notExists()) {
            path.writeLines(lines)
        }

        if (glossaryUuid != null) {
            val gType = when (translatorId) {
                TranslatorId.Baidu -> "baidu.g"
                TranslatorId.Youdao -> "youdao.g"
                TranslatorId.Gpt -> "gpt.g"
            }
            val gPath = root / novelId / "$volumeId.unpack" / gType / chapterId
            if (gPath.parent.notExists()) {
                gPath.parent.createDirectories()
            }
            if (gPath.notExists()) {
                gPath.writeText(
                    Json.encodeToString(ChapterGlossary(glossaryUuid, glossary))
                )
            }
        }
    }

    suspend fun makeTranslationVolumeFile(
        novelId: String,
        volumeId: String,
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

        suspend fun getJpLines(chapterId: String): List<String> {
            return getChapter(novelId, volumeId, chapterId)!!
        }

        suspend fun getZhLinesList(chapterId: String): List<List<String>> {
            suspend fun getChapter(translationId: TranslatorId): List<String>? {
                val type = when (translationId) {
                    TranslatorId.Baidu -> "baidu"
                    TranslatorId.Youdao -> "youdao"
                    TranslatorId.Gpt -> "gpt"
                }
                return getChapter(novelId, volumeId, type, chapterId)
            }

            return when (translationsMode) {
                NovelFileTranslationsMode.Parallel ->
                    translations.mapNotNull { getChapter(it) }

                NovelFileTranslationsMode.Priority ->
                    translations.firstNotNullOfOrNull { getChapter(it) }
                        ?.let { listOf(it) }
                        ?: emptyList()
            }
        }

        if (isTxt(volumeId)) {
            val zhPath = root / novelId / "$volumeId.unpack" / "${zhFilename}.txt"

            if (zhPath.notExists()) {
                zhPath.createFile()
            }

            zhPath.bufferedWriter().use { bf ->
                listUnpackedChapters(novelId, volumeId, "jp").sorted().forEach { chapterId ->
                    val zhLinesList = getZhLinesList(chapterId)
                    if (zhLinesList.isEmpty()) {
                        bf.appendLine("// 该分段翻译缺失。")
                    } else {
                        val linesList = when (lang) {
                            NovelFileLangV2.Jp -> throw RuntimeException("文库小说不允许日语下载")
                            NovelFileLangV2.Zh -> zhLinesList
                            NovelFileLangV2.JpZh -> listOf(getJpLines(chapterId)) + zhLinesList
                            NovelFileLangV2.ZhJp -> zhLinesList + listOf(getJpLines(chapterId))
                        }
                        for (i in 0 until linesList.first().size) {
                            linesList.forEach { lines ->
                                bf.appendLine(lines[i])
                            }
                        }
                    }
                }
            }
            return@withContext "${zhFilename}.txt"
        } else {
            val zhPath = root / novelId / "$volumeId.unpack" / "${zhFilename}.epub"
            val jpPath = root / novelId / volumeId

            val unpackChapters = listUnpackedChapters(novelId, volumeId, "jp")
            Epub.modify(srcPath = jpPath, dstPath = zhPath) { entry, bytesIn ->
                // 为了兼容ChapterId以斜杠开头的旧格式
                val chapterId = if ("/${entry.name}".escapePath() in unpackChapters) {
                    "/${entry.name}".escapePath()
                } else if (entry.name.escapePath() in unpackChapters) {
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