package api

import data.web.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.ZoneId

@Resource("/prepare-book/{providerId}/{novelId}/{lang}/{type}")
private data class PrepareBook(
    val providerId: String,
    val novelId: String,
    val lang: BookFileLang,
    val type: BookFileType,
)

fun Route.routePrepareBook() {
    val service by inject<PrepareBookService>()

    get<PrepareBook> { loc ->
        val result = service.updateBookFile(loc.providerId, loc.novelId, loc.lang, loc.type)
        result.onSuccess {
            call.respondRedirect(it)
        }.onFailure {
            call.respondResult(result)
        }
    }
}

class PrepareBookService(
    private val webNovelMetadataRepository: WebNovelMetadataRepository,
    private val webChapterRepository: WebChapterRepository,
    private val webNovelFileRepository: WebNovelFileRepository,
) {
    suspend fun updateBookFile(
        providerId: String,
        novelId: String,
        lang: BookFileLang,
        type: BookFileType,
    ): Result<String> {
        val fileName = "${providerId}.${novelId}.${lang.value}.${type.value}"

        val metadata = webNovelMetadataRepository.findOne(providerId, novelId)
            ?: return httpNotFound("小说不存在")

        val shouldMake = webNovelFileRepository.getCreationTime(fileName)?.let { fileCreateAt ->
            val updateAt = metadata.changeAt.atZone(ZoneId.systemDefault()).toInstant()
            updateAt > fileCreateAt
        } ?: true

        if (shouldMake) {
            val episodes = metadata.toc
                .mapNotNull { it.chapterId }
                .mapNotNull { chapterId ->
                    webChapterRepository
                        .getLocal(providerId, novelId, chapterId)
                        ?.let { chapterId to it }
                }
                .toMap()
            webNovelFileRepository.makeFile(
                fileName = fileName,
                lang = lang,
                type = type,
                metadata = metadata,
                episodes = episodes,
            )
        }

        return Result.success("../../../../../files-web/$fileName")
    }
}
