package api

import data.UserRepository
import data.web.WebChapterRepository
import data.web.WebNovelMetadataRepository
import data.wenku.WenkuNovelMetadataRepository
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

        @Resource("/{providerId}/{novelId}")
        data class Book(
            val parent: FavoritedWeb,
            val providerId: String,
            val novelId: String,
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

        @Resource("/{novelId}")
        data class Book(
            val parent: FavoritedWenku,
            val novelId: String,
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
            val result = service.setFavoriteWebBook(jwtUser.username, loc.providerId, loc.novelId)
            call.respondResult(result)
        }
        delete<R.FavoritedWeb.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavoriteWebBook(jwtUser.username, loc.providerId, loc.novelId)
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
            val result = service.setFavoriteWenkuBook(jwtUser.username, loc.novelId)
            call.respondResult(result)
        }
        delete<R.FavoritedWenku.Book> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavoriteWenkuBook(jwtUser.username, loc.novelId)
            call.respondResult(result)
        }
    }
}

class UserService(
    private val userRepo: UserRepository,
    private val webRepo: WebNovelMetadataRepository,
    private val wenkuRepo: WenkuNovelMetadataRepository,
    private val webEpisodeRepo: WebChapterRepository,
) {
    suspend fun listFavoriteWebBook(
        username: String,
        page: Int,
        pageSize: Int,
    ): Result<WebNovelService.NovelListPageDto> {
        val novels = userRepo.listFavoriteWebNovel(username)
            ?: return httpNotFound("用户不存在")
        val items = novels
            .asSequence()
            .drop(page * pageSize)
            .take(pageSize)
            .toList()
            .mapNotNull {
                val metadata = webRepo.findOne(it.providerId, it.novelId)
                    ?: return@mapNotNull null
                WebNovelService.NovelListPageDto.ItemDto(
                    providerId = metadata.providerId,
                    novelId = metadata.novelId,
                    titleJp = metadata.titleJp,
                    titleZh = metadata.titleZh,
                    total = metadata.toc.count { it.chapterId != null },
                    count = webEpisodeRepo.count(metadata.providerId, metadata.novelId),
                    countBaidu = webEpisodeRepo.countBaidu(metadata.providerId, metadata.novelId),
                    countYoudao = webEpisodeRepo.countYoudao(metadata.providerId, metadata.novelId),
                )
            }
        val dto = WebNovelService.NovelListPageDto(
            pageNumber = (novels.size / pageSize).toLong() + 1,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun setFavoriteWebBook(username: String, providerId: String, novelId: String): Result<Unit> {
        if (!webRepo.exist(providerId, novelId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.addFavoriteWebNovel(
            username = username,
            providerId = providerId,
            novelId = novelId,
        )
        return if (updateResult.matchedCount == 0L) {
            httpNotFound("用户不存在")
        } else {
            Result.success(Unit)
        }
    }

    suspend fun removeFavoriteWebBook(username: String, providerId: String, novelId: String): Result<Unit> {
        if (!webRepo.exist(providerId, novelId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.removeFavoriteWebNovel(
            username = username,
            providerId = providerId,
            novelId = novelId,
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
    ): Result<WenkuNovelService.NovelListPageDto> {
        val novels = userRepo.listFavoriteWenkuNovel(username)
            ?: return httpNotFound("用户不存在")
        val items = novels
            .asSequence()
            .drop(page * pageSize)
            .take(pageSize)
            .toList()
            .mapNotNull {
                val metadata = wenkuRepo.findOne(it)
                    ?: return@mapNotNull null
                WenkuNovelService.NovelListPageDto.ItemDto(
                    id = metadata.id.toHexString(),
                    title = metadata.title,
                    titleZh = metadata.titleZh,
                    cover = metadata.cover,
                )
            }
        val dto = WenkuNovelService.NovelListPageDto(
            pageNumber = (novels.size / pageSize).toLong() + 1,
            items = items,
        )
        return Result.success(dto)
    }

    suspend fun setFavoriteWenkuBook(username: String, novelId: String): Result<Unit> {
        if (!wenkuRepo.exist(novelId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.addFavoriteWenkuNovel(
            username = username,
            novelId = novelId,
        )
        return if (updateResult.matchedCount == 0L) {
            httpNotFound("用户不存在")
        } else {
            Result.success(Unit)
        }
    }

    suspend fun removeFavoriteWenkuBook(username: String, novelId: String): Result<Unit> {
        if (!wenkuRepo.exist(novelId)) {
            return httpNotFound("书不存在")
        }
        val updateResult = userRepo.removeFavoriteWenkuNovel(
            username = username,
            novelId = novelId,
        )
        return if (updateResult.matchedCount == 0L) {
            httpNotFound("用户不存在")
        } else {
            Result.success(Unit)
        }
    }
}

