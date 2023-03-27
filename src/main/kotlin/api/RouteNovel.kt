package api

import data.*
import data.BookAuthor
import data.BookTocItem
import data.elasticsearch.EsBookMetadataRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.ZoneId

@Serializable
@Resource("/novel")
private class Novel {
    @Serializable
    @Resource("/list")
    data class List(
        val parent: Novel = Novel(),
        val page: Int,
        val provider: String = "",
        val query: String? = null,
    )

    @Serializable
    @Resource("/favorite")
    class Favorite(
        val parent: Novel = Novel(),
    )

    @Serializable
    @Resource("/favorite-item")
    class FavoriteItem(
        val parent: Novel = Novel(),
        val providerId: String,
        val bookId: String,
    )

    @Serializable
    @Resource("/rank/{providerId}")
    data class Rank(
        val parent: Novel = Novel(),
        val providerId: String,
    )

    @Serializable
    @Resource("/state/{providerId}/{bookId}")
    data class State(
        val parent: Novel = Novel(),
        val providerId: String,
        val bookId: String,
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
            queryString = loc.query?.ifBlank { null },
            providerId = loc.provider.ifEmpty { null },
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    authenticate {
        get<Novel.Favorite> {
            val username = call.jwtUsername()
            val result = service.listFavorite(username)
            call.respondResult(result)
        }
        post<Novel.FavoriteItem> { loc ->
            val username = call.jwtUsername()
            val result = service.addFavorite(username, loc.providerId, loc.bookId)
            call.respondResult(result)
        }
        delete<Novel.FavoriteItem> { loc ->
            val username = call.jwtUsername()
            val result = service.removeFavorite(username, loc.providerId, loc.bookId)
            call.respondResult(result)
        }
    }

    get<Novel.Rank> { loc ->
        val options = call.request.queryParameters.toMap().mapValues { it.value.first() }
        val result = service.listRank(loc.providerId, options)
        call.caching = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600 * 2))
        call.respondResult(result)
    }

    get<Novel.State> { loc ->
        val result = service.getState(loc.providerId, loc.bookId)
        call.respondResult(result)
    }

    authenticate(optional = true) {
        get<Novel.Metadata> { loc ->
            val username = call.jwtUsernameOrNull()
            val result = service.getMetadata(loc.providerId, loc.bookId, username)
            call.respondResult(result)
        }
    }

    authenticate {
        put<Novel.Metadata> { loc ->
            val username = call.jwtUsername()
            val patch = call.receive<NovelService.BookMetadataPatchBody>()
            val result = service.patchMetadata(loc.providerId, loc.bookId, patch, username)
            call.respondResult(result)
        }
    }

    get<Novel.Episode> { loc ->
        val result = service.getEpisode(loc.providerId, loc.bookId, loc.episodeId)
        call.respondResult(result)
    }

    authenticate {
        put<Novel.Episode> { loc ->
            val patch = call.receive<NovelService.BookEpisodePatchBody>()
            val result = service.patchEpisode(
                providerId = loc.providerId,
                bookId = loc.bookId,
                episodeId = loc.episodeId,
                patch = patch,
            )
            call.respondResult(result)
        }
    }
}

class NovelService(
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
    private val userRepository: UserRepository,
    private val bookPatchRepository: BookPatchRepository,
    private val esBookMetadataRepository: EsBookMetadataRepository,
) {
    @Serializable
    data class BookListPageDto(
        val pageNumber: Long,
        val items: List<ItemDto>,
    ) {
        @Serializable
        data class ItemDto(
            val providerId: String,
            val bookId: String,
            val titleJp: String,
            val titleZh: String?,
            val total: Int,
            val countJp: Int,
            val countZh: Int,
        )
    }

    suspend fun list(
        queryString: String?,
        providerId: String?,
        page: Int,
        pageSize: Int,
    ): Result<BookListPageDto> {
        val esPage = esBookMetadataRepository.search(
            queryString = queryString,
            providerId = providerId,
            page = page.coerceAtLeast(0),
            pageSize = 10,
        )
        val items = esPage.items.map {
            BookListPageDto.ItemDto(
                providerId = it.providerId,
                bookId = it.bookId,
                titleJp = it.titleJp,
                titleZh = it.titleZh,
                total = bookMetadataRepository.getLocal(it.providerId, it.bookId)!!.toc.count { it.episodeId != null },
                countJp = bookEpisodeRepository.countJp(it.providerId, it.bookId).toInt(),
                countZh = bookEpisodeRepository.countZh(it.providerId, it.bookId).toInt()
            )
        }
        val dto = BookListPageDto(
            pageNumber = esPage.total / pageSize,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun listFavorite(
        username: String,
    ): Result<BookListPageDto> {
        val user = userRepository.getByUsername(username)
            ?: return httpNotFound("用户不存在")
        val items = user.favoriteBooks.mapNotNull {
            val metadata = bookMetadataRepository.getLocal(
                providerId = it.providerId,
                bookId = it.bookId,
            ) ?: return@mapNotNull null

            BookListPageDto.ItemDto(
                providerId = metadata.providerId,
                bookId = metadata.bookId,
                titleJp = metadata.titleJp,
                titleZh = metadata.titleZh,
                total = metadata.toc.count { it.episodeId != null },
                countJp = bookEpisodeRepository.countJp(it.providerId, it.bookId).toInt(),
                countZh = bookEpisodeRepository.countZh(it.providerId, it.bookId).toInt()
            )
        }
        val dto = BookListPageDto(
            pageNumber = 1,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun addFavorite(
        username: String,
        providerId: String,
        bookId: String,
    ): Result<Unit> {
        userRepository.addFavorite(
            username = username,
            providerId = providerId,
            bookId = bookId,
        )
        return Result.success(Unit)
    }

    suspend fun removeFavorite(
        username: String,
        providerId: String,
        bookId: String,
    ): Result<Unit> {
        userRepository.removeFavorite(
            username = username,
            providerId = providerId,
            bookId = bookId,
        )
        return Result.success(Unit)
    }

    @Serializable
    data class BookRankPageDto(
        val pageNumber: Long,
        val items: List<ItemDto>,
    ) {
        @Serializable
        data class ItemDto(
            val providerId: String,
            val bookId: String,
            val titleJp: String,
            val titleZh: String?,
            val extra: String,
        )
    }

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<BookRankPageDto> {
        return bookMetadataRepository.listRank(
            providerId = providerId,
            options = options,
        ).map {
            BookRankPageDto(
                pageNumber = 1,
                items = it.map {
                    BookRankPageDto.ItemDto(
                        providerId = providerId,
                        bookId = it.bookId,
                        titleJp = it.titleJp,
                        titleZh = it.titleZh,
                        extra = it.extra,
                    )
                }
            )
        }
    }

    @Serializable
    data class BookStateDto(
        val total: Int,
        val countJp: Long,
        val countZh: Long,
    )

    suspend fun getState(
        providerId: String,
        bookId: String,
    ): Result<BookStateDto> {
        val metadata = bookMetadataRepository.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }
        return Result.success(
            BookStateDto(
                total = metadata.toc.count { it.episodeId != null },
                countJp = bookEpisodeRepository.countJp(metadata.providerId, metadata.bookId),
                countZh = bookEpisodeRepository.countZh(metadata.providerId, metadata.bookId),
            )
        )
    }

    @Serializable
    data class BookMetadataDto(
        val titleJp: String,
        val titleZh: String? = null,
        val authors: List<BookAuthor>,
        val introductionJp: String,
        val introductionZh: String? = null,
        val glossary: Map<String, String>,
        val toc: List<BookTocItem>,
        val visited: Long,
        val downloaded: Long,
        val syncAt: Long,
        val inFavorite: Boolean?,
    )

    suspend fun getMetadata(
        providerId: String,
        bookId: String,
        username: String?,
    ): Result<BookMetadataDto> {
        val user = username?.let { userRepository.getByUsername(it) }

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
                glossary = metadata.glossary,
                toc = metadata.toc,
                visited = metadata.visited,
                downloaded = metadata.downloaded,
                syncAt = metadata.syncAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
                inFavorite = user?.favoriteBooks?.any { it.providerId == providerId && it.bookId == bookId },
            )
        )
    }

    @Serializable
    data class BookMetadataPatchBody(
        val title: String? = null,
        val introduction: String? = null,
        val glossary: Map<String, String>? = null,
        val toc: Map<String, String>,
    )

    suspend fun patchMetadata(
        providerId: String,
        bookId: String,
        patch: BookMetadataPatchBody,
        username: String,
    ): Result<BookMetadataDto> {
        if (patch.title == null &&
            patch.introduction == null &&
            patch.glossary == null &&
            patch.toc.isEmpty()
        ) return httpInternalServerError("修改为空")

        bookPatchRepository.addMetadataPatch(
            providerId = providerId,
            bookId = bookId,
            title = patch.title,
            glossary = patch.glossary,
            introduction = patch.introduction,
            toc = patch.toc,
        )

        return getMetadata(
            providerId = providerId,
            bookId = bookId,
            username = username,
        )
    }

    @Serializable
    data class BookEpisodeDto(
        val titleJp: String,
        val titleZh: String? = null,
        val prevId: String? = null,
        val nextId: String? = null,
        val paragraphsJp: List<String>,
        val paragraphsZh: List<String>? = null,
    )

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

    @Serializable
    data class BookEpisodePatchBody(
        val paragraphs: Map<Int, String>
    )

    suspend fun patchEpisode(
        providerId: String,
        bookId: String,
        episodeId: String,
        patch: BookEpisodePatchBody,
    ): Result<BookEpisodeDto> {
        if (patch.paragraphs.isEmpty())
            return httpInternalServerError("修改为空")

        bookPatchRepository.addEpisodePatch(
            providerId = providerId,
            bookId = bookId,
            episodeId = episodeId,
            paragraphs = patch.paragraphs,
        )

        return getEpisode(
            providerId = providerId,
            bookId = bookId,
            episodeId = episodeId,
        )
    }
}
