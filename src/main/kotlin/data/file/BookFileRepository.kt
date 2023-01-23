package data.file

import data.BookEpisode
import data.BookMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.io.path.*

@Serializable
enum class BookFileLang(val value: String) {
    @SerialName("jp")
    JP("jp"),

    @SerialName("zh")
    ZH("zh"),

    @SerialName("mix")
    MIX("mix")
}

@Serializable
enum class BookFileType(val value: String) {
    @SerialName("epub")
    EPUB("epub"),

    @SerialName("txt")
    TXT("txt")
}

class BookFileRepository {
    private fun buildFilePath(fileName: String) =
        Path("./data/files") / fileName

    suspend fun makeFile(
        fileName: String,
        lang: BookFileLang,
        type: BookFileType,
        metadata: BookMetadata,
        episodes: Map<String, BookEpisode>,
    ) {
        val filePath = buildFilePath(fileName)
        when (type) {
            BookFileType.EPUB -> makeEpubFile(filePath, lang, metadata, episodes)
            BookFileType.TXT -> makeTxtFile(filePath, lang, metadata, episodes)
        }
    }

    fun getCreationTime(fileName: String): Instant? {
        val filePath = buildFilePath(fileName)
        return if (filePath.exists()) {
            filePath.readAttributes<BasicFileAttributes>()
                .creationTime()
                .toInstant()
        } else null
    }
}