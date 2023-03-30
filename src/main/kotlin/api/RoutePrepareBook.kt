package api

import data.web.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.ZoneId
import kotlin.io.path.*

@Serializable
@Resource("/prepare-book/{providerId}/{bookId}/{lang}/{type}")
private data class PrepareBook(
    val providerId: String,
    val bookId: String,
    val lang: BookFileLang,
    val type: BookFileType,
)

fun Route.routePrepareBook() {
    val service by inject<PrepareBookService>()

    get<PrepareBook> { loc ->
        val result = service.updateBookFile(loc.providerId, loc.bookId, loc.lang, loc.type)
        result.onSuccess { fileName -> return@get call.respondRedirect("../../../../../files/$fileName") }
        call.respondResult(result)
    }
}

class PrepareBookService(
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
    private val webBookFileRepository: WebBookFileRepository,
) {
    suspend fun updateBookFile(
        providerId: String,
        bookId: String,
        lang: BookFileLang,
        type: BookFileType,
    ): Result<String> {
        val fileName = "${providerId}.${bookId}.${lang.value}.${type.value}"

        val metadata = bookMetadataRepository.getLocal(providerId, bookId)
            ?: return httpNotFound("小说不存在")

        val shouldMake = webBookFileRepository.getCreationTime(fileName)?.let { fileCreateAt ->
            val updateAt = metadata.changeAt.atZone(ZoneId.systemDefault()).toInstant()
            updateAt > fileCreateAt
        } ?: true

        if (shouldMake) {
            val episodes = metadata.toc
                .mapNotNull { it.episodeId }
                .mapNotNull { episodeId ->
                    bookEpisodeRepository
                        .getLocal(providerId, bookId, episodeId)
                        ?.let { episodeId to it }
                }
                .toMap()
            webBookFileRepository.makeFile(
                fileName = fileName,
                lang = lang,
                type = type,
                metadata = metadata,
                episodes = episodes,
            )
        }

        bookMetadataRepository.increaseDownloaded(providerId, bookId)
        return Result.success(fileName)
    }
}
