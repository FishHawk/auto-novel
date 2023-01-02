package data.make

import data.BookEpisode
import data.BookMetadata
import java.nio.file.Path

data class BookFile(
    val metadata: BookMetadata,
    val episodes: Map<String, BookEpisode>,
    val lang: Lang,
    val type: Type,
) {
    enum class Lang(val value: String) {
        JP("jp"),
        ZH("zh"),
        MIX("mix")
    }

    enum class Type(val value: String) {
        EPUB("epub"),
        TXT("txt")
    }
}

suspend fun makeFile(
    bookFile: BookFile,
    filePath: Path,
) {
    when (bookFile.type) {
        BookFile.Type.EPUB -> makeEpubFile(filePath, bookFile)
        BookFile.Type.TXT -> makeTxtFile(filePath, bookFile)
    }
}