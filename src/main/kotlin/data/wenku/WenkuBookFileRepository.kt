package data.wenku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.*

class WenkuBookFileRepository(
    private val root: Path
) {
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
        bookId: String,
        fileName: String,
    ) = withContext(Dispatchers.IO) {
        val filePath = root / bookId / fileName
        return@withContext runCatching {
            filePath.deleteIfExists()
        }
    }

    fun list(novelId: String): List<String> {
        val path = root / novelId
        val fileExtensions = listOf("epub", "txt")
        return if (path.exists() && path.isDirectory()) {
            path.listDirectoryEntries()
                .filter { it.isRegularFile() && it.fileName.extension in fileExtensions }
                .map { it.fileName.toString() }
        } else {
            emptyList()
        }
    }
}