package api

import data.*
import data.web.EsBookMetadataRepository
import data.web.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.ZoneId

@Serializable
@Resource("/novel")
private class WebNovel {
    @Serializable
    @Resource("/list")
    data class List(
        val parent: WebNovel = WebNovel(),
        val page: Int,
        val pageSize: Int = 10,
        val provider: String = "",
        val query: String? = null,
    )

    @Serializable
    @Resource("/favorite")
    class Favorite(
        val parent: WebNovel = WebNovel(),
        val page: Int,
        val pageSize: Int = 10,
    )

    @Serializable
    @Resource("/favorite-item")
    class FavoriteItem(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
        val bookId: String,
    )

    @Serializable
    @Resource("/rank/{providerId}")
    data class Rank(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
    )

    @Serializable
    @Resource("/state/{providerId}/{bookId}")
    data class State(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
        val bookId: String,
    )

    @Serializable
    @Resource("/metadata/{providerId}/{bookId}")
    data class Metadata(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
        val bookId: String,
    )

    @Serializable
    @Resource("/episode/{providerId}/{bookId}/{episodeId}")
    data class Episode(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
        val bookId: String,
        val episodeId: String,
    )
}

fun Route.routeWebNovel() {
    val service by inject<WebNovelService>()

    get<WebNovel.List> { loc ->
        val result = service.list(
            queryString = loc.query?.ifBlank { null },
            providerId = loc.provider.ifEmpty { null },
            page = loc.page.coerceAtLeast(0),
            pageSize = loc.pageSize.coerceAtMost(20),
        )
        call.respondResult(result)
    }

    authenticate {
        get<WebNovel.Favorite> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listFavorite(
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceAtMost(20),
                username = jwtUser.username,
            )
            call.respondResult(result)
        }
        post<WebNovel.FavoriteItem> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.addFavorite(jwtUser.username, loc.providerId, loc.bookId)
            call.respondResult(result)
        }
        delete<WebNovel.FavoriteItem> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavorite(jwtUser.username, loc.providerId, loc.bookId)
            call.respondResult(result)
        }
    }

    get<WebNovel.Rank> { loc ->
        val options = call.request.queryParameters.toMap().mapValues { it.value.first() }
        val result = service.listRank(loc.providerId, options)
        call.caching = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600 * 2))
        call.respondResult(result)
    }

    get<WebNovel.State> { loc ->
        val result = service.getState(loc.providerId, loc.bookId)
        call.respondResult(result)
    }

    authenticate(optional = true) {
        get<WebNovel.Metadata> { loc ->
            val jwtUser = call.jwtUserOrNull()
            val result = service.getMetadata(loc.providerId, loc.bookId, jwtUser?.username)
            call.respondResult(result)
        }
    }

    authenticate {
        put<WebNovel.Metadata> { loc ->
            val jwtUser = call.jwtUser()
            val patch = call.receive<WebNovelService.BookMetadataPatchBody>()
            val result = service.patchMetadata(loc.providerId, loc.bookId, patch, jwtUser.username)
            call.respondResult(result)
        }
    }

    get<WebNovel.Episode> { loc ->
        val result = service.getEpisode(loc.providerId, loc.bookId, loc.episodeId)
        call.respondResult(result)
    }

//    authenticate {
//        put<WebNovel.Episode> { loc ->
//            val patch = call.receive<WebNovelService.BookEpisodePatchBody>()
//            val result = service.patchEpisode(
//                providerId = loc.providerId,
//                bookId = loc.bookId,
//                episodeId = loc.episodeId,
//                patch = patch,
//            )
//            call.respondResult(result)
//        }
//    }
}

class WebNovelService(
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
            val count: Long,
            val countBaidu: Long,
            val countYoudao: Long,
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
            pageSize = pageSize,
        )
        val items = esPage.items.map {
            BookListPageDto.ItemDto(
                providerId = it.providerId,
                bookId = it.bookId,
                titleJp = it.titleJp,
                titleZh = it.titleZh,
                total = bookMetadataRepository.getLocal(it.providerId, it.bookId)!!.toc.count { it.episodeId != null },
                count = bookEpisodeRepository.count(it.providerId, it.bookId),
                countBaidu = bookEpisodeRepository.countBaidu(it.providerId, it.bookId),
                countYoudao = bookEpisodeRepository.countYoudao(it.providerId, it.bookId),
            )
        }
        val dto = BookListPageDto(
            pageNumber = esPage.total / pageSize,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun listFavorite(
        page: Int,
        pageSize: Int,
        username: String,
    ): Result<BookListPageDto> {
        val user = userRepository.getByUsername(username)
            ?: return httpNotFound("用户不存在")
        val books = user.favoriteBooks
        val items = books
            .asSequence()
            .drop(pageSize * page)
            .take(pageSize)
            .toList()
            .mapNotNull {
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
                    count = bookEpisodeRepository.count(metadata.providerId, metadata.bookId),
                    countBaidu = bookEpisodeRepository.countBaidu(metadata.providerId, metadata.bookId),
                    countYoudao = bookEpisodeRepository.countYoudao(metadata.providerId, metadata.bookId),
                )
            }
        val dto = BookListPageDto(
            pageNumber = (books.size / pageSize).toLong() + 1,
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
        val count: Long,
        val countBaidu: Long,
        val countYoudao: Long,
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
                count = bookEpisodeRepository.count(metadata.providerId, metadata.bookId),
                countBaidu = bookEpisodeRepository.countBaidu(metadata.providerId, metadata.bookId),
                countYoudao = bookEpisodeRepository.countYoudao(metadata.providerId, metadata.bookId),
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
        val paragraphs: List<String>,
        val baiduParagraphs: List<String>? = null,
        val youdaoParagraphs: List<String>? = null,
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
                paragraphs = episode.paragraphs,
                baiduParagraphs = episode.baiduParagraphs,
                youdaoParagraphs = episode.youdaoParagraphs,
            )
        )
    }

//    @Serializable
//    data class BookEpisodePatchBody(
//        val paragraphs: Map<Int, String>
//    )
//
//    suspend fun patchEpisode(
//        providerId: String,
//        bookId: String,
//        episodeId: String,
//        patch: BookEpisodePatchBody,
//    ): Result<BookEpisodeDto> {
//        if (patch.paragraphs.isEmpty())
//            return httpInternalServerError("修改为空")
//
//        bookPatchRepository.addEpisodePatch(
//            providerId = providerId,
//            bookId = bookId,
//            episodeId = episodeId,
//            paragraphs = patch.paragraphs,
//        )
//
//        return getEpisode(
//            providerId = providerId,
//            bookId = bookId,
//            episodeId = episodeId,
//        )
//    }
}
