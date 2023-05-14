package api

import data.UserRepository
import data.web.WebBookEpisodeRepository
import data.web.WebBookMetadataRepository
import data.wenku.WenkuBookMetadataRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

@Resource("/user")
private class R {
    @Resource("/favorited-web")
    class FavoritedWeb(val parent: R = R()) {
        @Resource("/list")
        data class List(
            val parent: FavoritedWeb,
            val page: Int,
            val pageSize: Int = 10,
//        val sort:created|updated
//        val direction:asc|desc
        )

        @Resource("/{providerId}/{bookId}")
        data class Book(
            val parent: FavoritedWeb,
            val providerId: String,
            val bookId: String,
        )
    }

    @Resource("/favorited-wenku")
    class FavoritedWenku(val parent: R = R()) {
        @Resource("/list")
        data class List(
            val parent: FavoritedWenku,
            val page: Int,
            val pageSize: Int = 10,
        )

        @Resource("/{bookId}")
        data class Book(
            val parent: FavoritedWenku,
            val bookId: String,
        )
    }
}

fun Route.routeUser() {
    val service by inject<UserService>()

    authenticate {
        get<R.FavoritedWeb.List> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listFavoriteWebBook(
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceAtMost(20),
                username = jwtUser.username,
            )
            call.respondResult(result)
        }
        put<R.FavoritedWeb.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.setFavoriteWebBook(jwtUser.username, loc.providerId, loc.bookId)
            call.respondResult(result)
        }
        delete<R.FavoritedWeb.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavoriteWebBook(jwtUser.username, loc.providerId, loc.bookId)
            call.respondResult(result)
        }

        get<R.FavoritedWenku.List> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listFavoriteWenkuBook(
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceAtMost(20),
                username = jwtUser.username,
            )
            call.respondResult(result)
        }
        put<R.FavoritedWenku.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.setFavoriteWenkuBook(jwtUser.username, loc.bookId)
            call.respondResult(result)
        }
        delete<R.FavoritedWenku.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavoriteWenkuBook(jwtUser.username, loc.bookId)
            call.respondResult(result)
        }
    }
}

class UserService(
    private val userRepo: UserRepository,
    private val webRepo: WebBookMetadataRepository,
    private val wenkuRepo: WenkuBookMetadataRepository,
    private val webEpisodeRepo: WebBookEpisodeRepository,
) {
    suspend fun listFavoriteWebBook(
        username: String,
        page: Int,
        pageSize: Int,
    ): Result<WebNovelService.BookListPageDto> {
        val books = userRepo.listFavoriteWebBook(username)
            ?: return httpNotFound("用户不存在")
        val items = books
            .asSequence()
            .drop(page * pageSize)
            .take(pageSize)
            .toList()
            .mapNotNull {
                val metadata = webRepo.getLocal(it.providerId, it.bookId)
                    ?: return@mapNotNull null
                WebNovelService.BookListPageDto.ItemDto(
                    providerId = metadata.providerId,
                    bookId = metadata.bookId,
                    titleJp = metadata.titleJp,
                    titleZh = metadata.titleZh,
                    total = metadata.toc.count { it.episodeId != null },
                    count = webEpisodeRepo.count(metadata.providerId, metadata.bookId),
                    countBaidu = webEpisodeRepo.countBaidu(metadata.providerId, metadata.bookId),
                    countYoudao = webEpisodeRepo.countYoudao(metadata.providerId, metadata.bookId),
                )
            }
        val dto = WebNovelService.BookListPageDto(
            pageNumber = (books.size / pageSize).toLong() + 1,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun setFavoriteWebBook(username: String, providerId: String, bookId: String): Result<Unit> {
        if (!webRepo.exist(providerId, bookId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.addFavoriteWebBook(
            username = username,
            providerId = providerId,
            bookId = bookId,
        )
        return if (updateResult.matchedCount == 0L) {
            httpNotFound("用户不存在")
        } else {
            Result.success(Unit)
        }
    }

    suspend fun removeFavoriteWebBook(username: String, providerId: String, bookId: String): Result<Unit> {
        if (!webRepo.exist(providerId, bookId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.removeFavoriteWebBook(
            username = username,
            providerId = providerId,
            bookId = bookId,
        )
        return if (updateResult.matchedCount == 0L) {
            httpNotFound("用户不存在")
        } else {
            Result.success(Unit)
        }
    }

    suspend fun listFavoriteWenkuBook(
        username: String,
        page: Int,
        pageSize: Int,
    ): Result<WenkuNovelService.BookListPageDto> {
        val books = userRepo.listFavoriteWenkuBook(username)
            ?: return httpNotFound("用户不存在")
        val items = books
            .asSequence()
            .drop(page * pageSize)
            .take(pageSize)
            .toList()
            .mapNotNull {
                val metadata = wenkuRepo.findOne(it)
                    ?: return@mapNotNull null
                WenkuNovelService.BookListPageDto.ItemDto(
                    id = metadata.id.toHexString(),
                    title = metadata.title,
                    titleZh = metadata.titleZh,
                    cover = metadata.cover,
                )
            }
        val dto = WenkuNovelService.BookListPageDto(
            pageNumber = (books.size / pageSize).toLong() + 1,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun setFavoriteWenkuBook(username: String, bookId: String): Result<Unit> {
        if (!wenkuRepo.exist(bookId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.addFavoriteWenkuBook(
            username = username,
            bookId = bookId,
        )
        return if (updateResult.matchedCount == 0L) {
            httpNotFound("用户不存在")
        } else {
            Result.success(Unit)
        }
    }

    suspend fun removeFavoriteWenkuBook(username: String, bookId: String): Result<Unit> {
        if (!wenkuRepo.exist(bookId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.removeFavoriteWenkuBook(
            username = username,
            bookId = bookId,
        )
        return if (updateResult.matchedCount == 0L) {
            httpNotFound("用户不存在")
        } else {
            Result.success(Unit)
        }
    }
}

