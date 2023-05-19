package data.wenku

import data.web.BookFileLang
import data.web.BookFileType
import data.web.makeEpubFile
import data.web.makeTxtFile
import epub.EpubReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.lang.RuntimeException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
        val reader = EpubReader(filePath)
        reader.listXhtmlFiles().forEach { xhtmlPath ->
            val unpackTextPath = unpackPath / xhtmlPath.replace("/", ".")
            val doc = reader.getXhtmlFile(xhtmlPath)
            doc.select("rt").remove()
            val text = doc.select("p")
                .map { it.text() }
                .filter { it.isNotBlank() }
            if (text.isNotEmpty()) {
                unpackTextPath.writeLines(text)
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
        TODO()
        return@withContext "$fileName.unpack/${lang.value}.epub"
    }
}