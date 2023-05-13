package api

import data.*
import data.web.WebBookIndexRepository
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
import util.toOptional
import java.time.ZoneId
import java.util.*

@Resource("/novel")
private class WebNovel {
    @Resource("/list")
    data class List(
        val parent: WebNovel = WebNovel(),
        val page: Int,
        val pageSize: Int = 10,
        val provider: String = "",
        val query: String? = null,
    )

    @Resource("/favorite")
    class Favorite(
        val parent: WebNovel = WebNovel(),
        val page: Int,
        val pageSize: Int = 10,
    )

    @Resource("/favorite-item")
    class FavoriteItem(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
        val bookId: String,
    )

    @Resource("/rank/{providerId}")
    data class Rank(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
    )

    @Resource("/{providerId}/{bookId}")
    data class Book(
        val parent: WebNovel = WebNovel(),
        val providerId: String,
        val bookId: String,
    ) {
        @Resource("/state")
        data class State(val parent: Book)

        @Resource("/episode/{episodeId}")
        data class Episode(val parent: Book, val episodeId: String)
    }
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

    authenticate(optional = true) {
        get<WebNovel.Book> { loc ->
            val jwtUser = call.jwtUserOrNull()
            val result = service.getMetadata(loc.providerId, loc.bookId, jwtUser?.username)
            call.respondResult(result)
        }
    }

    authenticate {
        put<WebNovel.Book> { loc ->
            val jwtUser = call.jwtUser()
            val patch = call.receive<WebNovelService.BookMetadataPatchBody>()
            val result = service.patchMetadata(loc.providerId, loc.bookId, patch, jwtUser.username)
            call.respondResult(result)
        }
    }

    get<WebNovel.Book.State> { loc ->
        val result = service.getState(loc.parent.providerId, loc.parent.bookId)
        call.respondResult(result)
    }

    get<WebNovel.Book.Episode> { loc ->
        val result = service.getEpisode(loc.parent.providerId, loc.parent.bookId, loc.episodeId)
        call.respondResult(result)
    }
}

class WebNovelService(
    private val metadataRepo: WebBookMetadataRepository,
    private val episodeRepo: WebBookEpisodeRepository,
    private val userRepo: UserRepository,
    private val patchRepo: WebBookPatchRepository,
    private val indexRepo: WebBookIndexRepository,
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
        val esPage = indexRepo.search(
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
                total = metadataRepo.getLocal(
                    it.providerId,
                    it.bookId
                )!!.toc.count { it.episodeId != null },
                count = episodeRepo.count(it.providerId, it.bookId),
                countBaidu = episodeRepo.countBaidu(it.providerId, it.bookId),
                countYoudao = episodeRepo.countYoudao(it.providerId, it.bookId),
            )
        }
        val dto = BookListPageDto(
            pageNumber = (esPage.total / pageSize) + 1,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun listFavorite(
        page: Int,
        pageSize: Int,
        username: String,
    ): Result<BookListPageDto> {
        val user = userRepo.getByUsername(username)
            ?: return httpNotFound("用户不存在")
        val books = user.favoriteBooks
        val items = books
            .asSequence()
            .drop(pageSize * page)
            .take(pageSize)
            .toList()
            .mapNotNull {
                val metadata = metadataRepo.getLocal(
                    providerId = it.providerId,
                    bookId = it.bookId,
                ) ?: return@mapNotNull null

                BookListPageDto.ItemDto(
                    providerId = metadata.providerId,
                    bookId = metadata.bookId,
                    titleJp = metadata.titleJp,
                    titleZh = metadata.titleZh,
                    total = metadata.toc.count { it.episodeId != null },
                    count = episodeRepo.count(metadata.providerId, metadata.bookId),
                    countBaidu = episodeRepo.countBaidu(metadata.providerId, metadata.bookId),
                    countYoudao = episodeRepo.countYoudao(metadata.providerId, metadata.bookId),
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
        userRepo.addFavorite(
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
        userRepo.removeFavorite(
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
        return metadataRepo.listRank(
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
        val metadata = metadataRepo.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }
        return Result.success(
            BookStateDto(
                total = metadata.toc.count { it.episodeId != null },
                count = episodeRepo.count(metadata.providerId, metadata.bookId),
                countBaidu = episodeRepo.countBaidu(metadata.providerId, metadata.bookId),
                countYoudao = episodeRepo.countYoudao(metadata.providerId, metadata.bookId),
            )
        )
    }

    @Serializable
    data class BookMetadataDto(
        val titleJp: String,
        val titleZh: String?,
        val authors: List<Author>,
        val introductionJp: String,
        val introductionZh: String?,
        val glossary: Map<String, String>,
        val toc: List<TocItem>,
        val visited: Long,
        val downloaded: Long,
        val syncAt: Long,
        val inFavorite: Boolean?,
    ) {
        @Serializable
        data class Author(val name: String, val link: String?)

        @Serializable
        data class TocItem(val titleJp: String, val titleZh: String, val episodeId: String?)

    }

    private suspend fun createMetadataDto(
        metadata: WebBookMetadataRepository.BookMetadata,
        username: String?,
    ): BookMetadataDto {
        val user = username?.let { userRepo.getByUsername(it) }
        return BookMetadataDto(
            titleJp = metadata.titleJp,
            titleZh = metadata.titleZh,
            authors = metadata.authors.map { BookMetadataDto.Author(it.name, it.link) },
            introductionJp = metadata.introductionJp,
            introductionZh = metadata.introductionZh,
            glossary = metadata.glossary,
            toc = metadata.toc.map { BookMetadataDto.TocItem(it.titleJp, it.titleZh ?: "", it.episodeId) },
            visited = metadata.visited,
            downloaded = metadata.downloaded,
            syncAt = metadata.syncAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
            inFavorite = user?.favoriteBooks?.any { it.providerId == metadata.providerId && it.bookId == metadata.bookId },
        )
    }

    suspend fun getMetadata(
        providerId: String,
        bookId: String,
        username: String?,
    ): Result<BookMetadataDto> {
        val metadata = metadataRepo.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }
        metadataRepo.increaseVisited(providerId, bookId)
        val metadataDto = createMetadataDto(metadata, username)
        return Result.success(metadataDto)
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
        body: BookMetadataPatchBody,
        username: String,
    ): Result<BookMetadataDto> {
        if (body.title == null &&
            body.introduction == null &&
            body.glossary == null &&
            body.toc.isEmpty()
        ) return httpInternalServerError("修改为空")

        val metadata = metadataRepo.getLocal(providerId, bookId)
            ?: return httpNotFound("小说不存在")

        fun createTextChangeOrNull(jp: String, zhOld: String?, zhNew: String?): BookMetadataPatch.TextChange? {
            return if (zhNew != null && zhNew != zhOld) {
                BookMetadataPatch.TextChange(jp, zhOld, zhNew)
            } else null
        }

        val titleChange = createTextChangeOrNull(
            metadata.titleJp,
            metadata.titleZh,
            body.title,
        )
        val introductionChange = createTextChangeOrNull(
            metadata.introductionJp,
            metadata.introductionZh,
            body.introduction,
        )
        val glossaryChange = body.glossary?.takeIf {
            body.glossary != metadata.glossary
        }
        val tocChange = body.toc.mapNotNull { (jp, zhNew) ->
            metadata.toc.find { it.titleJp == jp }?.let { item ->
                BookMetadataPatch.TextChange(jp = item.titleJp, zhOld = item.titleZh, zhNew = zhNew)
            }
        }

        if (
            titleChange == null &&
            introductionChange == null &&
            glossaryChange == null &&
            tocChange.isEmpty()
        ) {
            return httpInternalServerError("修改为空")
        }

        // Add patch
        patchRepo.addPatch(
            providerId = providerId,
            bookId = bookId,
            titleJp = metadata.titleJp,
            titleZh = metadata.titleZh,
            titleChange = titleChange,
            introductionChange = introductionChange,
            glossaryChange = glossaryChange,
            tocChange = tocChange,
        )

        val tocZh = mutableMapOf<Int, String>()
        metadata.toc.forEachIndexed { index, item ->
            val newTitleZh = body.toc[item.titleJp]
            if (newTitleZh != null) {
                tocZh[index] = newTitleZh
            }
        }

        val newMetadata = metadataRepo.updateZh(
            providerId = providerId,
            bookId = bookId,
            titleZh = titleChange?.zhNew.toOptional(),
            introductionZh = introductionChange?.zhNew.toOptional(),
            glossary = glossaryChange.toOptional(),
            tocZh = tocZh,
        )

        val metadataDto = createMetadataDto(newMetadata!!, username)
        return Result.success(metadataDto)
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
        val metadata = metadataRepo.get(providerId, bookId)
            .getOrElse { return httpInternalServerError(it.message) }

        val toc = metadata.toc.filter { it.episodeId != null }
        val currIndex = toc.indexOfFirst { it.episodeId == episodeId }
        if (currIndex == -1) return httpInternalServerError("episode id not in toc")

        val episode = episodeRepo.get(providerId, bookId, episodeId)
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
}
