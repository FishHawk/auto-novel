package infra.wenku

import infra.model.NovelFileLang
import infra.model.TranslatorId
import infra.model.WenkuNovelVolumeJp
import infra.model.WenkuNovelVolumeList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import util.epub.Epub
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.io.path.*

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

    private suspend fun listUnpackedChapters(
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

    suspend fun listUntranslatedChapter(
        novelId: String,
        volumeId: String,
        translatorId: TranslatorId,
    ): List<String> {
        val type = when (translatorId) {
            TranslatorId.Baidu -> "baidu"
            TranslatorId.Youdao -> "youdao"
            TranslatorId.Gpt -> "gpt"
        }
        val jpChapters = listUnpackedChapters(novelId, volumeId, "jp")
        val zhChapters = listUnpackedChapters(novelId, volumeId, type)
        return jpChapters.filter { it !in zhChapters }
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
    }

    suspend fun makeTranslationVolumeFile(
        novelId: String,
        volumeId: String,
        lang: NovelFileLang,
    ) = withContext(Dispatchers.IO) {
        val type = when (lang) {
            NovelFileLang.ZH_BAIDU, NovelFileLang.MIX_BAIDU -> "baidu"
            NovelFileLang.ZH_YOUDAO, NovelFileLang.MIX_YOUDAO -> "youdao"
            NovelFileLang.ZH_GPT, NovelFileLang.MIX_GPT -> "gpt"
            NovelFileLang.MIX_ALL, NovelFileLang.JP -> throw RuntimeException()
        }
        val mix = when (lang) {
            NovelFileLang.ZH_BAIDU, NovelFileLang.ZH_YOUDAO, NovelFileLang.ZH_GPT -> false
            NovelFileLang.MIX_BAIDU, NovelFileLang.MIX_YOUDAO, NovelFileLang.MIX_GPT -> true
            NovelFileLang.MIX_ALL, NovelFileLang.JP -> throw RuntimeException()
        }

        suspend fun getChapterOrFallback(chapterId: String): Pair<List<String>?, Boolean> {
            val primary = getChapter(novelId, volumeId, type, chapterId)
            val fallback = listOf("gpt", "youdao", "baidu")
                .filter { it != type }
                .firstNotNullOfOrNull { getChapter(novelId, volumeId, it, chapterId) }
            return Pair(primary ?: fallback, primary == null)
        }

        if (isTxt(volumeId)) {
            val zhPath = root / novelId / "$volumeId.unpack" / "${lang.value}.txt"

            if (zhPath.notExists()) {
                zhPath.createFile()
            }

            zhPath.bufferedWriter().use {
                listUnpackedChapters(novelId, volumeId, "jp").sorted().forEach { chapterId ->
                    val jpLines = getChapter(novelId, volumeId, chapterId)!!
                    val (zhLines, isFallback) = getChapterOrFallback(chapterId)
                    if (zhLines == null) {
                        it.appendLine("//该分段缺失。")
                    } else if (mix) {
                        if (isFallback) {
                            it.appendLine("//该分段翻译不存在，使用备用翻译。")
                        }
                        jpLines.forEachIndexed { index, jpLine ->
                            it.appendLine(jpLine)
                            if (jpLine.isNotBlank()) it.appendLine(zhLines[index])
                        }
                    } else {
                        zhLines.forEach { zhLine -> it.appendLine(zhLine) }
                    }
                }
            }
            return@withContext "${lang.value}.txt"
        } else {
            val unpackChapters = listUnpackedChapters(novelId, volumeId, "jp")
            val zhPath = root / novelId / "$volumeId.unpack" / "${lang.value}.epub"
            val jpPath = root / novelId / volumeId
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
                    val (zhLines, _) = getChapterOrFallback(chapterId)
                    if (zhLines == null) {
                        bytesIn
                    } else {
                        val doc = Jsoup.parse(bytesIn.decodeToString(), Parser.xmlParser())
                        doc.select("p")
                            .filter { el -> el.text().isNotBlank() }
                            .forEachIndexed { index, el ->
                                if (mix) {
                                    el.before("<p>${zhLines[index]}<p>")
                                    el.attr("style", "opacity:0.4;")
                                } else {
                                    el.text(zhLines[index])
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
            return@withContext "${lang.value}.epub"
        }
    }
}