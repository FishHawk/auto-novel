package api

import data.BookAuthor
import data.BookRepository
import data.BookMetadata
import data.BookTocItem
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
private data class BookStateDto(
    val total: Int,
    val countJp: Long,
    val countZh: Long,
)

@Serializable
private data class BookPageItemDto(
    val providerId: String,
    val bookId: String,
    val titleJp: String,
    val titleZh: String? = null,
    val state: BookStateDto,
)

@Serializable
private data class BookPageDto(
    val total: Long,
    val items: List<BookPageItemDto>,
)

@Serializable
private data class BookMetadataDto(
    val titleJp: String,
    val titleZh: String? = null,
    val authors: List<BookAuthor>,
    val introductionJp: String,
    val introductionZh: String? = null,
    val toc: List<BookTocItem>,
    val visited: Long,
    val downloaded: Long,
    val syncAt: Long,
)

@Serializable
private data class BookEpisodeDto(
    val titleJp: String,
    val titleZh: String? = null,
    val prevId: String? = null,
    val nextId: String? = null,
    val paragraphsJp: List<String>,
    val paragraphsZh: List<String>? = null,
)

@Serializable
@Resource("/novel")
private class Novel {
    @Serializable
    @Resource("/state/{providerId}/{bookId}")
    data class State(
        val parent: Novel = Novel(),
        val providerId: String,
        val bookId: String,
    )

    @Serializable
    @Resource("/list")
    data class List(
        val parent: Novel = Novel(),
        val page: Int,
    )

    @Serializable
    @Resource("/metadata/{providerId}/{bookId}")
    data class Metadata(
        val parent: Novel = Novel(),
        val providerId: String,
        val bookId: String,
    )

    @Serializable
    @Resource("/episode/{providerId}/{bookId}/{episodeId}")
    data class Episode(
        val parent: Novel = Novel(),
        val providerId: String,
        val bookId: String,
        val episodeId: String,
    )
}

fun Route.routeNovel(bookRepo: BookRepository) {
    suspend fun getState(metadata: BookMetadata): BookStateDto {
        return BookStateDto(
            total = metadata.toc.count { it.episodeId != null },
            countJp = bookRepo.countEpisodeJp(providerId = metadata.providerId, bookId = metadata.bookId),
            countZh = bookRepo.countEpisodeZh(providerId = metadata.providerId, bookId = metadata.bookId),
        )
    }

    get<Novel.State> { loc ->
        val metadata = bookRepo.getMetadata(providerId = loc.providerId, bookId = loc.bookId)
        call.respond(getState(metadata))
    }

    get<Novel.List> { loc ->
        val total = bookRepo.countMetadata()
        bookRepo.list(page = loc.page.coerceAtLeast(0), pageSize = 10)
            .map {
                BookPageItemDto(
                    providerId = it.providerId,
                    bookId = it.bookId,
                    titleJp = it.titleJp,
                    titleZh = it.titleZh,
                    state = getState(it),
                )
            }
            .let {
                call.respond(BookPageDto(total = total, items = it))
            }
    }

    get<Novel.Metadata> { loc ->
        bookRepo.increaseVisited(providerId = loc.providerId, bookId = loc.bookId)
        val metadata = bookRepo.getMetadata(providerId = loc.providerId, bookId = loc.bookId)
        val dto = BookMetadataDto(
            titleJp = metadata.titleJp,
            titleZh = metadata.titleZh,
            authors = metadata.authors,
            introductionJp = metadata.introductionJp,
            introductionZh = metadata.introductionZh,
            toc = metadata.toc,
            visited = metadata.visited,
            downloaded = metadata.downloaded,
            syncAt = metadata.syncAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
        )
        call.respond(dto)
    }

    get<Novel.Episode> { loc ->
        val metadata = bookRepo.getMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )
        val episode = bookRepo.getEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
        )
        val toc = metadata.toc.filter { it.episodeId != null }


        val currIndex = toc.indexOfFirst { it.episodeId == loc.episodeId }
        val dto = BookEpisodeDto(
            titleJp = toc[currIndex].titleJp,
            titleZh = toc[currIndex].titleZh,
            prevId = toc.getOrNull(currIndex - 1)?.episodeId,
            nextId = toc.getOrNull(currIndex + 1)?.episodeId,
            paragraphsJp = episode.paragraphsJp,
            paragraphsZh = episode.paragraphsZh,
        )
        call.respond(dto)
    }
}
