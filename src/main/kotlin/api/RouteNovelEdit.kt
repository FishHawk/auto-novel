package api

import data.BookEpisodeRepository
import data.BookMetadataRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
@Resource("/novel-edit")
private class NovelEdit {
    @Serializable
    @Resource("/metadata/{providerId}/{bookId}")
    data class Metadata(
        val parent: NovelEdit = NovelEdit(),
        val providerId: String,
        val bookId: String,
    )

    @Serializable
    @Resource("/episode/{providerId}/{bookId}/{episodeId}")
    data class Episode(
        val parent: NovelEdit = NovelEdit(),
        val providerId: String,
        val bookId: String,
        val episodeId: String,
    )
}

fun Route.routeNovelEdit() {
    val service by inject<NovelEditService>()

    post<NovelEdit.Metadata> { loc ->
        val patch = call.receive<NovelEditService.BookMetadataPatchDto>()
        val result = service.patchMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
            patch = patch,
        )
        call.respondResult(result)
    }

    post<NovelEdit.Episode> { loc ->
        val patch = call.receive<NovelEditService.BookEpisodePatchDto>()
        val result = service.patchEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
            patch = patch,
        )
        call.respondResult(result)
    }
}

class NovelEditService(
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
) {
    @Serializable
    data class BookMetadataPatchDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: Map<Int, String>,
    )

    @Serializable
    data class BookEpisodePatchDto(
        val paragraphs: Map<Int, String>
    )

    suspend fun patchMetadata(
        providerId: String,
        bookId: String,
        patch: BookMetadataPatchDto,
    ): Result<Unit> {
        if (patch.title == null &&
            patch.introduction == null &&
            patch.toc.isEmpty()
        ) return Result.success(Unit)

        println(patch)
        return Result.success(Unit)
    }

    suspend fun patchEpisode(
        providerId: String,
        bookId: String,
        episodeId: String,
        patch: BookEpisodePatchDto
    ): Result<Unit> {
        println(patch)
        return Result.success(Unit)
    }
}
