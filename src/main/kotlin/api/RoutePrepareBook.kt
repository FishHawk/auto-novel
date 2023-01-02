package api

import data.BookRepository
import data.make.BookFile
import data.make.makeFile
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.nio.file.attribute.BasicFileAttributes
import java.time.ZoneId
import kotlin.io.path.*

@Serializable
@Resource("/prepare-book/{providerId}/{bookId}/{lang}/{type}")
private data class PrepareBook(
    val providerId: String,
    val bookId: String,
    val lang: String,
    val type: String,
)

fun Route.routePrepareBook(bookRepo: BookRepository) {
    get<PrepareBook> { loc ->
        val lang = when (loc.lang.lowercase()) {
            "jp" -> BookFile.Lang.JP
            "zh" -> BookFile.Lang.ZH
            "mix" -> BookFile.Lang.MIX
            else -> return@get call.respondText(
                "不支持${loc.lang}",
                status = HttpStatusCode.BadRequest,
            )
        }
        val type = when (loc.type.lowercase()) {
            "epub" -> BookFile.Type.EPUB
            "txt" -> BookFile.Type.TXT
            else -> return@get call.respondText(
                "不支持${loc.lang}",
                status = HttpStatusCode.BadRequest,
            )
        }

        val fileName = "${loc.providerId}.${loc.bookId}.${loc.lang}.${loc.type}"
        val filePath = Path("./data/files") / fileName

        val metadata = bookRepo.getMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )

        val shouldMake = if (filePath.exists()) {
            val updateAt = metadata.changeAt.atZone(ZoneId.systemDefault()).toInstant()
            val fileCreateAt = filePath.readAttributes<BasicFileAttributes>().creationTime().toInstant()
            updateAt > fileCreateAt
        } else true

        if (shouldMake) {
            val episodes = metadata.toc
                .mapNotNull { it.episodeId }
                .mapNotNull { episodeId ->
                    bookRepo.getEpisodeInDb(
                        providerId = loc.providerId,
                        bookId = loc.bookId,
                        episodeId = episodeId
                    )?.let { episodeId to it }
                }
                .toMap()
            val bookFile = BookFile(
                metadata = metadata,
                episodes = episodes,
                lang = lang,
                type = type,
            )
            makeFile(bookFile, filePath)
        }

        bookRepo.increaseDownloaded(providerId = loc.providerId, bookId = loc.bookId)
        call.respondRedirect("../../../../../files/$fileName")
    }
}

