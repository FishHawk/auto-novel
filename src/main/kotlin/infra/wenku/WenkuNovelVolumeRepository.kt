package infra.wenku

import infra.model.NovelFileLang
import infra.model.TranslationState
import infra.model.TranslatorId
import infra.model.WenkuNovelVolumeList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.epub.EpubReader
import util.epub.EpubWriter
import util.epub.copyTo
import java.io.OutputStream
import java.lang.RuntimeException
import kotlin.io.path.*

private fun String.escapePath() = replace('/', '.')

class WenkuNovelVolumeRepository {
    private val root = Path("./data/files-wenku")

    suspend fun list(
        novelId: String,
    ): WenkuNovelVolumeList = withContext(Dispatchers.IO) {
        val path = root / novelId
        val fileExtensions = listOf("epub", "txt")

        val volumesJp = mutableListOf<String>()
        val volumesZh = mutableListOf<String>()
        if (path.exists() && path.isDirectory()) {
            path.listDirectoryEntries()
                .filter { it.isRegularFile() && it.fileName.extension in fileExtensions }
                .forEach {
                    println(it)
                    val volumeId = it.fileName.toString()
                    if (hasUnpacked(novelId, volumeId)) {
                        volumesJp.add(volumeId)
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

    suspend fun getTranslationState(
        novelId: String,
        volumeId: String,
    ): TranslationState {
        return TranslationState(
            total = listUnpackedChapters(novelId, volumeId, "jp").size.toLong(),
            baidu = listUnpackedChapters(novelId, volumeId, "baidu").size.toLong(),
            youdao = listUnpackedChapters(novelId, volumeId, "youdao").size.toLong(),
        )
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

    suspend fun unpackVolume(
        novelId: String,
        volumeId: String,
    ) = withContext(Dispatchers.IO) {
        val filePath = root / novelId / volumeId
        val unpackPath = root / novelId / "$volumeId.unpack" / "jp"
        if (unpackPath.notExists()) {
            unpackPath.createDirectories()
        }
        EpubReader(filePath).use { reader ->
            reader.listXhtmlFiles().forEach { xhtmlPath ->
                val doc = reader.readFileAsXHtml(xhtmlPath)
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
        val jpChapters = listUnpackedChapters(novelId, volumeId, "jp")
        val zhChapters =
            when (translatorId) {
                TranslatorId.Baidu -> listUnpackedChapters(novelId, volumeId, "baidu")
                TranslatorId.Youdao -> listUnpackedChapters(novelId, volumeId, "youdao")
            }
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
        val zhPath = root / novelId / "$volumeId.unpack" / "${lang.value}.epub"
        val jpPath = root / novelId / volumeId

        val type = when (lang) {
            NovelFileLang.ZH_BAIDU, NovelFileLang.MIX_BAIDU -> "baidu"
            NovelFileLang.ZH_YOUDAO, NovelFileLang.MIX_YOUDAO -> "youdao"
            else -> throw RuntimeException()
        }
        val mix = when (lang) {
            NovelFileLang.ZH_BAIDU, NovelFileLang.ZH_YOUDAO -> false
            NovelFileLang.MIX_BAIDU, NovelFileLang.MIX_YOUDAO -> true
            else -> throw RuntimeException()
        }
        val unpackChapters = listUnpackedChapters(novelId, volumeId, type)

        EpubReader(jpPath).use { reader ->
            EpubWriter(zhPath, reader.getOpfPath()).use { writer ->
                writer.writeOpfFile(reader.readFileAsText(reader.getOpfPath()))
                reader.listFiles().forEach { path ->
                    // Css文件，删除
                    if (path.endsWith("css")) {
                        writer.writeTextFile(path, "")
                        return@forEach
                    }

                    val escapedPath = path.escapePath()
                    if (escapedPath in unpackChapters) {
                        // XHtml文件，尝试生成翻译版
                        val zhLines = getChapter(novelId, volumeId, type, escapedPath)
                        if (zhLines == null) {
                            reader.copyTo(writer, path)
                        } else {
                            val doc = reader.readFileAsXHtml(path)
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
                            writer.writeTextFile(path, doc.html())
                        }
                    } else {
                        // 其他文件
                        reader.copyTo(writer, path)
                    }
                }
            }
        }
        return@withContext "$volumeId.unpack/${lang.value}.epub"
    }
}