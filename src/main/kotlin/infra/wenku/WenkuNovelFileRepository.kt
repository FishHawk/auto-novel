package infra.wenku

import infra.model.NovelFileLang
import util.epub.EpubReader
import util.epub.EpubWriter
import util.epub.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.lang.RuntimeException
import java.nio.file.Path
import kotlin.io.path.*

private fun String.escapePath() = replace('/', '.')
private fun String.unescapePath() = replace('.', '/')

class WenkuNovelFileRepository(
    private val root: Path
) {
    private fun listVolume(novelId: String): List<String> {
        val path = root / novelId
        val fileExtensions = listOf("util/epub", "txt")
        return if (path.exists() && path.isDirectory()) {
            path.listDirectoryEntries()
                .filter { it.isRegularFile() && it.fileName.extension in fileExtensions }
                .map { it.fileName.toString() }
        } else {
            emptyList()
        }
    }

    private fun hasUnpacked(novelId: String, volumeId: String): Boolean {
        val unpackPath = root / novelId / "$volumeId.unpack"
        return unpackPath.exists()
    }

    suspend fun listVolumeZh(novelId: String) = withContext(Dispatchers.IO) {
        listVolume(novelId).filter { !hasUnpacked(novelId, it) }
    }

    suspend fun listVolumeJp(novelId: String) = withContext(Dispatchers.IO) {
        listVolume(novelId).filter { hasUnpacked(novelId, it) }
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

    suspend fun isUnpackChapterExist(
        novelId: String,
        volumeId: String,
        type: String,
        chapterId: String,
    ) = withContext(Dispatchers.IO) {
        val path = root / novelId / "$volumeId.unpack" / type / chapterId
        return@withContext path.exists()
    }

    suspend fun getUnpackedChapter(
        novelId: String,
        volumeId: String,
        type: String,
        chapterId: String,
    ) = withContext(Dispatchers.IO) {
        val path = root / novelId / "$volumeId.unpack" / type / chapterId
        return@withContext if (path.notExists()) null
        else path.readText().lines()
    }

    suspend fun createUnpackedChapter(
        novelId: String,
        volumeId: String,
        type: String,
        chapterId: String,
        lines: List<String>,
    ) = withContext(Dispatchers.IO) {
        val path = root / novelId / "$volumeId.unpack" / type / chapterId
        if (path.parent.notExists()) {
            path.parent.createDirectories()
        }
        if (path.notExists()) {
            path.writeLines(lines)
        }
    }

    suspend fun makeFile(
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
                        val zhLines = getUnpackedChapter(novelId, volumeId, type, escapedPath)
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