package api

import data.web.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.ZoneId

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
        result.onSuccess {
            call.respondRedirect(it)
        }.onFailure {
            call.respondResult(result)
        }
    }
}

class PrepareBookService(
    private val webBookMetadataRepository: WebBookMetadataRepository,
    private val webBookEpisodeRepository: WebBookEpisodeRepository,
    private val webBookFileRepository: WebBookFileRepository,
) {
    suspend fun updateBookFile(
        providerId: String,
        bookId: String,
        lang: BookFileLang,
        type: BookFileType,
    ): Result<String> {
        val fileName = "${providerId}.${bookId}.${lang.value}.${type.value}"

        val metadata = webBookMetadataRepository.getLocal(providerId, bookId)
            ?: return httpNotFound("小说不存在")

        val shouldMake = webBookFileRepository.getCreationTime(fileName)?.let { fileCreateAt ->
            val updateAt = metadata.changeAt.atZone(ZoneId.systemDefault()).toInstant()
            updateAt > fileCreateAt
        } ?: true

        if (shouldMake) {
            val episodes = metadata.toc
                .mapNotNull { it.episodeId }
                .mapNotNull { episodeId ->
                    webBookEpisodeRepository
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

        webBookMetadataRepository.increaseDownloaded(providerId, bookId)
        return Result.success("../../../../../files-web/$fileName")
    }
}
