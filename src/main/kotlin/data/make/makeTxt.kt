package data.make

import data.BookEpisode
import data.BookMetadata
import data.BookTocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.notExists

suspend fun makeTxtFile(filePath: Path, bookFile: BookFile) {
    withContext(Dispatchers.IO) {
        if (filePath.notExists()) {
            filePath.createFile()
        }
        filePath.bufferedWriter().use {
            val writer: TxtWriter = when (bookFile.lang) {
                BookFile.Lang.JP -> TxtMakerJp
                BookFile.Lang.ZH -> TxtMakerZh
                BookFile.Lang.MIX -> TxtMakerMix
            }
            with(it) { with(writer) { writeBook(bookFile) } }
        }
    }
}

private const val MISSING_EPISODE_HINT = "该章节缺失。\n\n"

private abstract class TxtWriter {
    abstract fun BufferedWriter.writeTitle(metadata: BookMetadata)
    abstract fun BufferedWriter.writeIntroduction(metadata: BookMetadata)
    abstract fun BufferedWriter.writeTocItemTitle(item: BookTocItem)
    abstract fun BufferedWriter.writeEpisode(episode: BookEpisode)

    fun BufferedWriter.writeAuthor(metadata: BookMetadata) {
        metadata.authors.forEach { author ->
            write(author.name)
            if (author.link != null) {
                write("[${author.link}]")
            }
            write("\n")
        }
    }

    fun BufferedWriter.writeMissingEpisode() {
        write(MISSING_EPISODE_HINT)
    }

    fun BufferedWriter.writeBook(book: BookFile) {
        writeTitle(book.metadata)
        write("\n")
        writeAuthor(book.metadata)
        write("\n")
        write("#".repeat(12) + "\n")
        writeIntroduction(book.metadata)
        write("\n\n\n")

        book.metadata.toc.forEach { item ->
            writeTocItemTitle(item)
            write("\n")
            val episode = item.episodeId?.let { book.episodes[it] }
            if (episode == null) writeMissingEpisode()
            else writeEpisode(episode)
            write("\n\n\n")
        }
    }
}

private object TxtMakerJp : TxtWriter() {
    override fun BufferedWriter.writeTitle(metadata: BookMetadata) {
        write(metadata.titleJp + "\n")
    }

    override fun BufferedWriter.writeIntroduction(metadata: BookMetadata) {
        if (metadata.introductionJp.isNotBlank()) {
            write(metadata.introductionJp)
            write("\n")
        }
    }

    override fun BufferedWriter.writeTocItemTitle(item: BookTocItem) {
        write("# ${item.titleJp}\n")
    }

    override fun BufferedWriter.writeEpisode(episode: BookEpisode) {
        episode.paragraphsJp.forEach { text -> write(text + "\n") }
    }
}

private object TxtMakerZh : TxtWriter() {
    override fun BufferedWriter.writeTitle(metadata: BookMetadata) {
        write(metadata.titleZh!! + "\n")
    }

    override fun BufferedWriter.writeIntroduction(metadata: BookMetadata) {
        if (metadata.introductionZh!!.isNotBlank()) {
            write(metadata.introductionZh)
            write("\n")
        }
    }

    override fun BufferedWriter.writeTocItemTitle(item: BookTocItem) {
        write("# ${item.titleZh!!}\n")
    }

    override fun BufferedWriter.writeEpisode(episode: BookEpisode) {
        if (episode.paragraphsZh == null) {
            writeMissingEpisode()
        } else {
            episode.paragraphsZh.forEach { text -> write(text + "\n") }
        }
    }
}

private object TxtMakerMix : TxtWriter() {
    override fun BufferedWriter.writeTitle(metadata: BookMetadata) {
        with(TxtMakerJp) { writeTitle(metadata) }
        with(TxtMakerZh) { writeTitle(metadata) }
    }

    override fun BufferedWriter.writeIntroduction(metadata: BookMetadata) {
        with(TxtMakerJp) { writeIntroduction(metadata) }
        with(TxtMakerZh) { writeIntroduction(metadata) }
    }

    override fun BufferedWriter.writeTocItemTitle(item: BookTocItem) {
        with(TxtMakerJp) { writeTocItemTitle(item) }
        with(TxtMakerZh) { writeTocItemTitle(item) }
    }

    override fun BufferedWriter.writeEpisode(episode: BookEpisode) {
        if (episode.paragraphsZh == null) {
            writeMissingEpisode()
        } else {
            episode.paragraphsJp.zip(episode.paragraphsZh).forEach { (textJp, textZh) ->
                if (textJp.isNotBlank()) {
                    write(textZh + "\n")
                    write(textJp + "\n")
                } else {
                    write(textJp + "\n")
                }
            }
        }
    }
}
