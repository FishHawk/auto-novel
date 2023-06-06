package api

import api.dto.PageDto
import api.dto.WebNovelOutlineDto
import api.dto.WenkuNovelOutlineDto
import infra.UserRepository
import infra.model.FavoriteListSort
import infra.web.WebNovelMetadataRepository
import infra.wenku.WenkuNovelMetadataRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

@Resource("/user")
private class UserRes {
    @Resource("/favorited-web")
    class FavoritedWeb(val parent: UserRes = UserRes()) {
        @Resource("/list")
        data class List(
            val parent: FavoritedWeb,
            val page: Int,
            val pageSize: Int = 10,
            val sort: FavoriteListSort,
        )

        @Resource("/{providerId}/{novelId}")
        data class Book(
            val parent: FavoritedWeb,
            val providerId: String,
            val novelId: String,
        )
    }

    @Resource("/favorited-wenku")
    class FavoritedWenku(val parent: UserRes = UserRes()) {
        @Resource("/list")
        data class List(
            val parent: FavoritedWenku,
            val page: Int,
            val pageSize: Int = 10,
            val sort: FavoriteListSort,
        )

        @Resource("/{novelId}")
        data class Book(
            val parent: FavoritedWenku,
            val novelId: String,
        )
    }
}

fun Route.routeUser() {
    val service by inject<UserApi>()

    authenticate {
        get<UserRes.FavoritedWeb.List> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listFavoriteWebBook(
                username = jwtUser.username,
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceAtMost(20),
                sort = loc.sort,
            )
            call.respondResult(result)
        }
        put<UserRes.FavoritedWeb.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.setFavoriteWebBook(jwtUser.username, loc.providerId, loc.novelId)
            call.respondResult(result)
        }
        delete<UserRes.FavoritedWeb.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavoriteWebBook(jwtUser.username, loc.providerId, loc.novelId)
            call.respondResult(result)
        }

        get<UserRes.FavoritedWenku.List> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listFavoriteWenkuBook(
                username = jwtUser.username,
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceAtMost(20),
                sort = loc.sort,
            )
            call.respondResult(result)
        }
        put<UserRes.FavoritedWenku.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.setFavoriteWenkuBook(jwtUser.username, loc.novelId)
            call.respondResult(result)
        }
        delete<UserRes.FavoritedWenku.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavoriteWenkuBook(jwtUser.username, loc.novelId)
            call.respondResult(result)
        }
    }
}

class UserApi(
    private val userRepo: UserRepository,
    private val webRepo: WebNovelMetadataRepository,
    private val wenkuRepo: WenkuNovelMetadataRepository,
) {
    suspend fun listFavoriteWebBook(
        username: String,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): Result<PageDto<WebNovelOutlineDto>> {
        val novelPage = userRepo.listFavoriteWebNovel(
            username = username,
            page = page,
            pageSize = pageSize,
            sort = sort,
        )
        val dto = PageDto.fromPage(novelPage, pageSize) {
            WebNovelOutlineDto.fromDomain(it)
        }
        return Result.success(dto)
    }

    suspend fun setFavoriteWebBook(username: String, providerId: String, novelId: String): Result<Unit> {
        val id = webRepo.get(providerId, novelId)?.id
            ?: return httpNotFound("书不存在")
        userRepo.addFavoriteWebNovel(
            username = username,
            novelId = id.toHexString(),
        )
        return Result.success(Unit)
    }

    suspend fun removeFavoriteWebBook(username: String, providerId: String, novelId: String): Result<Unit> {
        val id = webRepo.get(providerId, novelId)?.id
            ?: return httpNotFound("书不存在")
        userRepo.removeFavoriteWebNovel(
            username = username,
            novelId = id.toHexString(),
        )
        return Result.success(Unit)
    }

    suspend fun listFavoriteWenkuBook(
        username: String,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): Result<PageDto<WenkuNovelOutlineDto>> {
        val novelPage = userRepo.listFavoriteWenkuNovel(
            username = username,
            page = page,
            pageSize = pageSize,
            sort = sort,
        )
        val dto = PageDto.fromPage(novelPage, pageSize) {
            WenkuNovelOutlineDto.fromDomain(it)
        }
        return Result.success(dto)
    }

    suspend fun setFavoriteWenkuBook(username: String, novelId: String): Result<Unit> {
        if (!wenkuRepo.exist(novelId)) {
            return httpNotFound("书不存在")
        }
        userRepo.addFavoriteWenkuNovel(
            username = username,
            novelId = novelId,
        )
        return Result.success(Unit)
    }

    suspend fun removeFavoriteWenkuBook(username: String, novelId: String): Result<Unit> {
        if (!wenkuRepo.exist(novelId)) {
            return httpNotFound("书不存在")
        }
        userRepo.removeFavoriteWenkuNovel(
            username = username,
            novelId = novelId,
        )
        return Result.success(Unit)
    }
}

