package infra.wenku

import infra.model.NovelFileLang
import util.epub.EpubReader
import util.epub.EpubWriter
import util.epub.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.RuntimeException
import java.nio.file.Path
import kotlin.io.path.*

private fun String.escapePath() = replace('/', '.')
private fun String.unescapePath() = replace('.', '/')

class WenkuNovelFileRepository(
    private val root: Path
) {
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

}