package api

import data.*
import data.BookAuthor
import data.BookMetadata
import data.BookTocItem
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.ZoneId

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
        val provider: String = "",
        val sort: BookMetadataRepository.ListOption.Sort =
            BookMetadataRepository.ListOption.Sort.CreatedTime,
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

fun Route.routeNovel() {
    val service by inject<NovelService>()

    get<Novel.List> { loc ->
        val result = service.list(
            page = loc.page,
            pageSize = 10,
            optionProvider = loc.provider.ifEmpty { null },
            optionSort = loc.sort,
        )
        call.respondResult(result)
    }

    get<Novel.State> { loc ->
        val result = service.getState(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )
        call.respondResult(result)
    }

    get<Novel.Metadata> { loc ->
        val result = service.getMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )
        call.respondResult(result)
    }

    get<Novel.Episode> { loc ->
        val result = service.getEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
        )
        call.respondResult(result)
    }
}

class NovelService(
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
) {
    @Serializable
    data class BookStateDto(
        val total: Int,
        val countJp: Long,
        val countZh: Long,
    )

    @Serializable
    data class BookPageItemDto(
        val providerId: String,
        val bookId: String,
        val titleJp: String,
        val titleZh: String? = null,
        val state: BookStateDto,
    )

    @Serializable
    data class BookPageDto(
        val total: Long,
        val items: List<BookPageItemDto>,
    )

    @Serializable
    data class BookMetadataDto(
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
    data class BookEpisodeDto(
        val titleJp: String,
        val titleZh: String? = null,
        val prevId: String? = null,
        val nextId: String? = null,
        val paragraphsJp: List<String>,
        val paragraphsZh: List<String>? = null,
    )

    private suspend fun getStateFromMetadata(metadata: BookMetadata): BookStateDto {
        return BookStateDto(
            total = metadata.toc.count { it.episodeId != null },
            countJp = bookEpisodeRepository.countJp(metadata.providerId, metadata.bookId),
            countZh = bookEpisodeRepository.countZh(metadata.providerId, metadata.bookId),
        )
    }

    suspend fun list(
        page: Int,
        pageSize: Int,
        optionProvider: String?,
        optionSort: BookMetadataRepository.ListOption.Sort,
    ): Result<BookPageDto> {
        val items = bookMetadataRepository.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
            option = BookMetadataRepository.ListOption(
                providerId = optionProvider,
                sort = optionSort,
            ),
        ).map {
            BookPageItemDto(
                providerId = it.providerId,
                bookId = it.bookId,
                titleJp = it.titleJp,
                titleZh = it.titleZh,
                state = getStateFromMetadata(it),
            )
        }
        val total = if (optionProvider == null) {
            bookMetadataRepository.count()
        } else {
            bookMetadataRepository.countProvider(optionProvider)
        }
        return Result.success(BookPageDto(total = total, items = items))
    }

    suspend fun getState(
        providerId: String,
        bookId: String,
    ): Result<BookStateDto> {
        val metadata = bookMetadataRepository.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }
        return Result.success(getStateFromMetadata(metadata))
    }

    suspend fun getMetadata(
        providerId: String,
        bookId: String,
    ): Result<BookMetadataDto> {
        val metadata = bookMetadataRepository.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }
        bookMetadataRepository.increaseVisited(providerId, bookId)
        return Result.success(
            BookMetadataDto(
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
        )
    }

    suspend fun getEpisode(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): Result<BookEpisodeDto> {
        val metadata = bookMetadataRepository.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }

        val toc = metadata.toc.filter { it.episodeId != null }
        val currIndex = toc.indexOfFirst { it.episodeId == episodeId }
        if (currIndex == -1) return httpInternalServerError("episode id not in toc")

        val episode = bookEpisodeRepository.get(providerId, bookId, episodeId)
            .getOrElse { return httpInternalServerError(it.message) }

        return Result.success(
            BookEpisodeDto(
                titleJp = toc[currIndex].titleJp,
                titleZh = toc[currIndex].titleZh,
                prevId = toc.getOrNull(currIndex - 1)?.episodeId,
                nextId = toc.getOrNull(currIndex + 1)?.episodeId,
                paragraphsJp = episode.paragraphsJp,
                paragraphsZh = episode.paragraphsZh,
            )
        )
    }
}
