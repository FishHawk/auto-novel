package api

import data.BookEpisodeRepository
import data.BookMetadataRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
@Resource("/update-zh")
private class UpdateZh {
    @Serializable
    @Resource("/metadata/{providerId}/{bookId}")
    data class Metadata(
        val parent: UpdateZh = UpdateZh(),
        val providerId: String,
        val bookId: String,
        val startIndex: Int = 0,
        val endIndex: Int = 65536,
    )

    @Serializable
    @Resource("/episode/{providerId}/{bookId}/{episodeId}")
    data class Episode(
        val parent: UpdateZh = UpdateZh(),
        val providerId: String,
        val bookId: String,
        val episodeId: String,
    )
}

fun Route.routeUpdateZh() {
    val service by inject<UpdateZhService>()

    get<UpdateZh.Metadata> { loc ->
        val result = service.getMetadataToTranslate(
            providerId = loc.providerId,
            bookId = loc.bookId,
            startIndex = loc.startIndex,
            endIndex = loc.endIndex,
        )
        call.respondResult(result)
    }

    post<UpdateZh.Metadata> { loc ->
        val metadataTranslated = call.receive<UpdateZhService.MetadataTranslatedDto>()
        val result = service.updateMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
            metadataTranslated = metadataTranslated,
        )
        call.respondResult(result)
    }

    get<UpdateZh.Episode> { loc ->
        val result = service.getEpisodeToTranslate(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
        )
        call.respondResult(result)
    }

    post<UpdateZh.Episode> { loc ->
        val episodeTranslated = call.receive<List<String>>()
        val result = service.updateEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
            episodeTranslated = episodeTranslated,
        )
        call.respondResult(result)
    }
}

class UpdateZhService(
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
) {
    @Serializable
    data class MetadataToTranslateDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: List<String>,
        val episodeIds: List<String>,
    )

    @Serializable
    data class MetadataTranslatedDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: Map<String, String>,
    )

    suspend fun getMetadataToTranslate(
        providerId: String,
        bookId: String,
        startIndex: Int,
        endIndex: Int,
    ): Result<MetadataToTranslateDto> {
        val metadata = bookMetadataRepository.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }

        val episodeIds = metadata.toc
            .mapNotNull { it.episodeId }
            .safeSubList(startIndex, endIndex)
            .filter { episodeId ->
                val episode = bookEpisodeRepository.getLocal(providerId, bookId, episodeId)
                episode?.paragraphsZh == null
            }

        return Result.success(
            MetadataToTranslateDto(
                title = metadata.titleJp.takeIf { metadata.titleZh == null },
                introduction = metadata.introductionJp.takeIf { metadata.introductionZh == null },
                toc = metadata.toc.mapNotNull { if (it.titleZh == null) it.titleJp else null }.distinct(),
                episodeIds = episodeIds,
            )
        )
    }

    suspend fun updateMetadata(
        providerId: String,
        bookId: String,
        metadataTranslated: MetadataTranslatedDto,
    ): Result<Unit> {
        val metadata = bookMetadataRepository.getLocal(providerId, bookId)
            ?: return Result.success(Unit)

        val titleZh = metadataTranslated.title.takeIf {
            metadata.titleZh == null
        }
        val introductionZh = metadataTranslated.introduction.takeIf {
            metadata.introductionZh == null
        }
        val tocZh = metadata.toc.mapIndexedNotNull { index, item ->
            if (item.titleZh == null) {
                metadataTranslated.toc[item.titleJp]?.let { index to it }
            } else {
                null
            }
        }.toMap()

        if (titleZh == null &&
            introductionZh == null &&
            tocZh.isEmpty()
        ) {
            return Result.success(Unit)
        }

        bookMetadataRepository.updateZh(
            providerId = providerId,
            bookId = bookId,
            titleZh = metadataTranslated.title,
            introductionZh = metadataTranslated.introduction,
            tocZh = tocZh,
        )
        return Result.success(Unit)
    }

    suspend fun getEpisodeToTranslate(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): Result<List<String>> {
        val episode = bookEpisodeRepository.get(providerId, bookId, episodeId)
            .getOrElse { return httpInternalServerError(it.message) }
        return Result.success(
            if (episode.paragraphsZh != null) emptyList()
            else episode.paragraphsJp
        )
    }

    suspend fun updateEpisode(
        providerId: String,
        bookId: String,
        episodeId: String,
        episodeTranslated: List<String>,
    ): Result<Unit> {
        val episode = bookEpisodeRepository.getLocal(providerId, bookId, episodeId)

        if (episode == null ||
            episode.paragraphsZh != null ||
            episode.paragraphsJp.size != episodeTranslated.size
        ) {
            return Result.success(Unit)
        }

        bookEpisodeRepository.updateZh(
            providerId = providerId,
            bookId = bookId,
            episodeId = episodeId,
            paragraphsZh = episodeTranslated,
        )
        return Result.success(Unit)
    }
}
