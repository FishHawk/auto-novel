package api

import data.BookMetadata
import data.BookRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

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

fun Route.routeUpdateJp(bookRepo: BookRepository) {
    post<UpdateJp.Metadata> { loc ->
        val metadata = bookRepo.getMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )
        val episodeIds = metadata.toc
            .mapNotNull { it.episodeId }
            .safeSubList(loc.startIndex, loc.endIndex)
            .filter {
                bookRepo.getEpisodeInDb(
                    providerId = loc.providerId,
                    bookId = loc.bookId,
                    episodeId = it,
                ) == null
            }
        call.respond(episodeIds)
    }

    post<UpdateJp.Episode> { loc ->
        bookRepo.getEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
        )
        call.respond("成功")
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