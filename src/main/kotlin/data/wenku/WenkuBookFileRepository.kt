package data.wenku

import data.web.BookFileLang
import epub.EpubReader
import epub.EpubResource
import epub.EpubWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.lang.RuntimeException
import java.nio.file.Path
import kotlin.io.path.*

class WenkuBookFileRepository(
    private val root: Path
) {
    suspend fun list(
        novelId: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val path = root / novelId
        val fileExtensions = listOf("epub", "txt")
        return@withContext if (path.exists() && path.isDirectory()) {
            path.listDirectoryEntries()
                .filter { it.isRegularFile() && it.fileName.extension in fileExtensions }
                .map { it.fileName.toString() }
        } else {
            emptyList()
        }
    }

    suspend fun createAndOpen(
        novelId: String,
        fileName: String,
    ): Result<OutputStream> = withContext(Dispatchers.IO) {
        val novelPath = root / novelId
        if (!novelPath.exists()) {
            runCatching { novelPath.createDirectories() }
                .onFailure { return@withContext Result.failure(it) }
        }
        val filePath = novelPath / fileName
        return@withContext runCatching {
            filePath.createFile().outputStream()
        }
    }

    suspend fun delete(
        novelId: String,
        fileName: String,
    ) = withContext(Dispatchers.IO) {
        val filePath = root / novelId / fileName
        return@withContext runCatching {
            filePath.deleteIfExists()
        }
    }

    suspend fun unpackEpub(
        novelId: String,
        fileName: String,
    ) = withContext(Dispatchers.IO) {
        val filePath = root / novelId / fileName
        val unpackPath = root / novelId / "$fileName.unpack" / "jp"
        if (unpackPath.notExists()) {
            unpackPath.createDirectories()
        }
        EpubReader(filePath).use { reader ->
            reader.listXhtmlFiles().forEach { xhtmlPath ->
                val doc = reader.readFileAsXHtml(xhtmlPath)
                doc.select("rt").remove()
                val text = doc.body().select("p")
                    .mapNotNull { it.text().ifBlank { null } }
                if (text.isNotEmpty()) {
                    val unpackTextPath = unpackPath / xhtmlPath.replace("/", ".")
                    unpackTextPath.writeLines(text)
                }
            }
        }
    }

    suspend fun listUnpackItems(
        novelId: String,
        fileName: String,
        version: String,
    ) = withContext(Dispatchers.IO) {
        val unpackPath = root / novelId / "$fileName.unpack" / version
        return@withContext if (unpackPath.notExists()) emptyList()
        else unpackPath.listDirectoryEntries()
            .map { it.fileName.toString() }
    }

    suspend fun getUnpackItem(
        novelId: String,
        fileName: String,
        version: String,
        itemName: String,
    ) = withContext(Dispatchers.IO) {
        val path = root / novelId / "$fileName.unpack" / version / itemName
        return@withContext if (path.notExists()) null
        else path.readText()
    }

    suspend fun createUnpackItem(
        novelId: String,
        fileName: String,
        version: String,
        itemName: String,
        content: List<String>,
    ) = withContext(Dispatchers.IO) {
        val path = root / novelId / "$fileName.unpack" / version / itemName
        if (path.parent.notExists()) {
            path.parent.createDirectories()
        }
        if (path.notExists()) path.writeLines(content)
    }

    suspend fun makeFile(
        novelId: String,
        fileName: String,
        lang: BookFileLang,
    ) = withContext(Dispatchers.IO) {
        val zhFilePath = root / novelId / "$fileName.unpack" / "${lang.value}.epub"
        val filePath = root / novelId / fileName

        val version = when (lang) {
            BookFileLang.ZH_BAIDU, BookFileLang.MIX_BAIDU -> "baidu"
            BookFileLang.ZH_YOUDAO, BookFileLang.MIX_YOUDAO -> "youdao"
            else -> throw RuntimeException()
        }
        val mix = when (lang) {
            BookFileLang.ZH_BAIDU, BookFileLang.ZH_YOUDAO -> false
            BookFileLang.MIX_BAIDU, BookFileLang.MIX_YOUDAO -> true
            else -> throw RuntimeException()
        }
        val unpackItems = listUnpackItems(novelId, fileName, version)

        EpubReader(filePath).use { reader ->
            EpubWriter(zhFilePath, reader.getOpfPath()).use { writer ->
                writer.writeOpfFile(reader.readFileAsText(reader.getOpfPath()))
                reader.listFiles().forEach { path ->
                    if (path.endsWith("css")) {
                        writer.writeTextFile(path, "")
                        return@forEach
                    }

                    val escapedPath = path.replace("/", ".")
                    if (escapedPath in unpackItems) {
                        val zh = getUnpackItem(novelId, fileName, version, escapedPath)!!.lines()
                        val doc = reader.readFileAsXHtml(path)
                        doc.select("p")
                            .filter { el -> el.text().isNotBlank() }
                            .forEachIndexed { index, el ->
                                if (mix) {
                                    el.before("<p>${zh[index]}<p>")
                                    el.attr("style", "opacity:0.4;")
                                } else {
                                    el.text(zh[index])
                                }
                            }
                        doc.outputSettings().prettyPrint(true)
                        writer.writeTextFile(
                            path,
                            doc.html(),
                        )
                    } else {
                        writer.writeBinaryFile(
                            path,
                            reader.readFileAsBinary(path)
                        )
                    }
                }
            }
        }
        return@withContext "$fileName.unpack/${lang.value}.epub"
    }
}