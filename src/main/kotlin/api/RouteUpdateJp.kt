package api

import data.BookEpisodeRepository
import data.BookMetadataRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
@Resource("/update-jp")
private class UpdateJp {
    @Serializable
    @Resource("/metadata/{providerId}/{bookId}")
    data class Metadata(
        val parent: UpdateJp = UpdateJp(),
        val providerId: String,
        val bookId: String,
        val startIndex: Int = 0,
        val endIndex: Int = 65536,
    )

    @Serializable
    @Resource("/episode/{providerId}/{bookId}/{episodeId}")
    data class Episode(
        val parent: UpdateJp = UpdateJp(),
        val providerId: String,
        val bookId: String,
        val episodeId: String,
    )
}

fun Route.routeUpdateJp() {
    val service by inject<UpdateJpService>()

    post<UpdateJp.Metadata> { loc ->
        val result = service.updateMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
            startIndex = loc.startIndex,
            endIndex = loc.endIndex
        )
        call.respondResult(result)
    }

    post<UpdateJp.Episode> { loc ->
        val result = service.updateEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
        )
        call.respondResult(result)
    }
}

class UpdateJpService(
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
) {
    suspend fun updateMetadata(
        providerId: String,
        bookId: String,
        startIndex: Int,
        endIndex: Int,
    ): Result<List<String>> {
        val metadata = bookMetadataRepository.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }

        val episodeIds = metadata.toc
            .mapNotNull { it.episodeId }
            .safeSubList(startIndex, endIndex)
            .filter { bookEpisodeRepository.getLocal(providerId, bookId, it) == null }

        return Result.success(episodeIds)
    }

    suspend fun updateEpisode(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): Result<Unit> {
        bookEpisodeRepository.get(providerId, bookId, episodeId)
            .getOrElse { return httpInternalServerError(it.message) }
        return Result.success(Unit)
    }
}

fun <T> List<T>.safeSubList(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex >= size || toIndex <= 0 || fromIndex >= toIndex) {
        return emptyList()
    }
    return subList(
        fromIndex.coerceAtLeast(0),
        toIndex.coerceAtMost(size)
    )
}