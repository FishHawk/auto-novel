package api

import api.model.WebNovelOutlineDto
import api.model.WenkuNovelOutlineDto
import api.model.asDto
import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import infra.common.UserRepository
import infra.model.FavoriteListSort
import infra.model.User
import infra.model.UserFavored
import infra.web.WebNovelMetadataRepository
import infra.wenku.WenkuNovelMetadataRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject
import java.util.*

@Resource("/user")
private class UserRes {
    @Resource("")
    class List(
        val parent: UserRes,
        val page: Int,
        val pageSize: Int,
        val role: User.Role,
    )

    @Resource("/read-history")
    class ReadHistoryWeb(val parent: UserRes) {
        @Resource("")
        class List(
            val parent: ReadHistoryWeb,
            val page: Int,
            val pageSize: Int,
        )

        @Resource("/{providerId}/{novelId}")
        class Novel(
            val parent: ReadHistoryWeb,
            val providerId: String,
            val novelId: String,
        )
    }

    @Resource("/favored")
    class Favored(val parent: UserRes)

    @Resource("/favored-web")
    class FavoredWeb(val parent: UserRes) {
        @Resource("/{favoredId}")
        class Id(
            val parent: FavoredWeb,
            val favoredId: String,
        ) {
            @Resource("")
            class List(
                val parent: Id,
                val page: Int,
                val pageSize: Int,
                val sort: FavoriteListSort,
            )

            @Resource("/{providerId}/{novelId}")
            class Novel(
                val parent: Id,
                val providerId: String,
                val novelId: String,
            )
        }
    }

    @Resource("/favored-wenku")
    class FavoredWenku(val parent: UserRes) {
        @Resource("/{favoredId}")
        class Id(
            val parent: FavoredWenku,
            val favoredId: String,
        ) {
            @Resource("")
            class List(
                val parent: Id,
                val page: Int,
                val pageSize: Int,
                val sort: FavoriteListSort,
            )

            @Resource("/{novelId}")
            class Novel(
                val parent: Id,
                val novelId: String,
            )
        }
    }
}

fun Route.routeUser() {
    val service by inject<UserApi>()

    authenticateDb {
        get<UserRes.List> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listUser(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    role = loc.role,
                )
            }
        }

        get<UserRes.ReadHistoryWeb.List> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listReadHistoryWeb(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                )
            }
        }
        put<UserRes.ReadHistoryWeb.Novel> { loc ->
            val user = call.authenticatedUser()
            val chapterId = call.receive<String>()
            call.tryRespond {
                service.updateReadHistoryWeb(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                    chapterId = chapterId,
                )
            }
        }

        get<UserRes.Favored> {
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listFavored(
                    user = user,
                )
            }
        }

        post<UserRes.FavoredWeb> {
            @Serializable
            class Body(val title: String)

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.createFavoredWeb(
                    user = user,
                    title = body.title,
                )
            }
        }
        put<UserRes.FavoredWeb.Id> { loc ->
            @Serializable
            class Body(val title: String)

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.updateFavoredWeb(
                    user = user,
                    favoredId = loc.favoredId,
                    title = body.title,
                )
            }
        }
        delete<UserRes.FavoredWeb.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavoredWeb(
                    user = user,
                    favoredId = loc.favoredId,
                )
            }
        }
        get<UserRes.FavoredWeb.Id.List> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listFavoredWebNovel(
                    user = user,
                    favoredId = loc.parent.favoredId,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    sort = loc.sort,
                )
            }
        }
        put<UserRes.FavoredWeb.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavoredWebNovel(
                    user = user,
                    favoredId = loc.parent.favoredId,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                )
            }
        }
        delete<UserRes.FavoredWeb.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavoredWebNovel(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                )
            }
        }

        post<UserRes.FavoredWenku> {
            @Serializable
            class Body(val title: String)

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.createFavoredWenku(
                    user = user,
                    title = body.title,
                )
            }
        }
        put<UserRes.FavoredWenku.Id> { loc ->
            @Serializable
            class Body(val title: String)

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.updateFavoredWenku(
                    user = user,
                    favoredId = loc.favoredId,
                    title = body.title,
                )
            }
        }
        delete<UserRes.FavoredWenku.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavoredWenku(
                    user = user,
                    favoredId = loc.favoredId,
                )
            }
        }
        get<UserRes.FavoredWenku.Id.List> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listFavoredWenkuNovel(
                    user = user,
                    favoredId = loc.parent.favoredId,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    sort = loc.sort,
                )
            }
        }
        put<UserRes.FavoredWenku.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavoredWenkuNovel(
                    user = user,
                    favoredId = loc.parent.favoredId,
                    novelId = loc.novelId,
                )
            }
        }
        delete<UserRes.FavoredWenku.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavoredWenkuNovel(
                    user = user,
                    novelId = loc.novelId,
                )
            }
        }
    }
}

class UserApi(
    private val userRepo: UserRepository,
    private val webMetadataRepo: WebNovelMetadataRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
) {
    @Serializable
    data class UserOutlineDto(
        val id: String,
        val email: String,
        val username: String,
        val role: User.Role,
        val createdAt: Long,
    )

    suspend fun listUser(
        user: AuthenticatedUser,
        page: Int,
        pageSize: Int,
        role: User.Role,
    ): PageDto<UserOutlineDto> {
        user.shouldBeAtLeast(User.Role.Admin)
        return userRepo.listUser(
            page = page,
            pageSize = pageSize,
            role = role,
        ).asDto(pageSize) {
            UserOutlineDto(
                id = it.id.toHexString(),
                email = it.email,
                username = it.username,
                role = it.role,
                createdAt = it.createdAt.epochSeconds,
            )
        }
    }

    private fun throwNovelNotFound(): Nothing =
        throwNotFound("小说不存在")

    /*
     * Read history web
     */
    suspend fun listReadHistoryWeb(
        user: AuthenticatedUser,
        page: Int,
        pageSize: Int,
    ): PageDto<WebNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return userRepo
            .listReaderHistoryWebNovel(
                userId = user.id,
                page = page,
                pageSize = pageSize,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun updateReadHistoryWeb(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        chapterId: String,
    ) {
        val novel = webMetadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        userRepo.updateReadHistoryWebNovel(
            userId = user.id,
            novelId = novel.id.toHexString(),
            chapterId = chapterId,
        )
    }


    /*
     * Favored
     */
    @Serializable
    data class UserFavoredList(
        val web: List<UserFavored>,
        val wenku: List<UserFavored>,
    )

    suspend fun listFavored(user: AuthenticatedUser): UserFavoredList {
        val user = userRepo.getById(user.id)!!
        return UserFavoredList(
            web = user.favoredWeb,
            wenku = user.favoredWenku,
        )
    }

    /*
     * Favored web
     */
    suspend fun createFavoredWeb(
        user: AuthenticatedUser,
        title: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (title.length > 20) throwBadRequest("收藏夹标题至多为20个字符")
        if (user.favoredWeb.size >= 10) throwBadRequest("收藏夹最多只能创建10个")

        userRepo.updateFavoredWeb(
            userId = user.id,
            favored = user.favoredWeb + listOf(
                UserFavored(
                    id = UUID.randomUUID().toString(),
                    title = title,
                )
            )
        )
    }

    suspend fun updateFavoredWeb(
        user: AuthenticatedUser,
        favoredId: String,
        title: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (title.length > 20) throwBadRequest("收藏夹标题至多为20个字符")

        userRepo.updateFavoredWeb(
            userId = user.id,
            favored = user.favoredWeb.map {
                if (it.id == favoredId) it.copy(title = title) else it
            },
        )
    }

    suspend fun deleteFavoredWeb(
        user: AuthenticatedUser,
        favoredId: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (favoredId == "default") throwBadRequest("不可以删除默认收藏夹")

        userRepo.updateFavoredWeb(
            userId = user.id,
            favored = user.favoredWeb.filter { it.id != favoredId },
        )
    }

    suspend fun listFavoredWebNovel(
        user: AuthenticatedUser,
        favoredId: String,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): PageDto<WebNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return userRepo
            .listFavoriteWebNovel(
                userId = user.id,
                favoredId = favoredId,
                page = page,
                pageSize = pageSize,
                sort = sort,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun updateFavoredWebNovel(
        user: AuthenticatedUser,
        favoredId: String,
        providerId: String,
        novelId: String,
    ) {
        val novel = webMetadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()

        val total = userRepo.countFavoriteWebNovelByUserId(
            userId = user.id,
            favoredId = favoredId,
        )
        if (total >= 5000) {
            throwBadRequest("收藏夹已达到上限")
        }
        userRepo.updateFavoriteWebNovel(
            userId = ObjectId(user.id),
            novelId = novel.id,
            favoredId = favoredId,
            updateAt = novel.updateAt,
        )
    }

    suspend fun deleteFavoredWebNovel(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
    ) {
        val novel = webMetadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        userRepo.deleteFavoriteWebNovel(
            userId = ObjectId(user.id),
            novelId = novel.id,
        )
    }

    /*
     * Favored wenku
     */
    suspend fun createFavoredWenku(
        user: AuthenticatedUser,
        title: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (title.length > 20) throwBadRequest("收藏夹标题至多为20个字符")
        if (user.favoredWenku.size >= 10) throwBadRequest("收藏夹最多只能创建10个")

        userRepo.updateFavoredWenku(
            userId = user.id,
            favored = user.favoredWenku + listOf(
                UserFavored(
                    id = UUID.randomUUID().toString(),
                    title = title,
                )
            )
        )
    }

    suspend fun updateFavoredWenku(
        user: AuthenticatedUser,
        favoredId: String,
        title: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (title.length > 20) throwBadRequest("收藏夹标题至多为20个字符")

        userRepo.updateFavoredWenku(
            userId = user.id,
            favored = user.favoredWenku.map {
                if (it.id == favoredId) it.copy(title = title) else it
            },
        )
    }

    suspend fun deleteFavoredWenku(
        user: AuthenticatedUser,
        favoredId: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (favoredId == "default") throwBadRequest("不可以删除默认收藏夹")

        userRepo.updateFavoredWenku(
            userId = user.id,
            favored = user.favoredWenku.filter { it.id != favoredId },
        )
    }

    suspend fun listFavoredWenkuNovel(
        user: AuthenticatedUser,
        favoredId: String,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): PageDto<WenkuNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return userRepo
            .listFavoriteWenkuNovel(
                userId = user.id,
                favoredId = favoredId,
                page = page,
                pageSize = pageSize,
                sort = sort,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun updateFavoredWenkuNovel(
        user: AuthenticatedUser,
        favoredId: String,
        novelId: String,
    ) {
        val novel = wenkuMetadataRepo.get(novelId)
            ?: throwNovelNotFound()

        val total = userRepo.countFavoriteWenkuNovelByUserId(
            userId = user.id,
            favoredId = favoredId,
        )
        if (total >= 5000) {
            throwBadRequest("收藏夹已达到上限")
        }
        userRepo.updateFavoriteWenkuNovel(
            userId = ObjectId(user.id),
            novelId = novel.id,
            favoredId = favoredId,
            updateAt = novel.updateAt,
        )
    }

    suspend fun deleteFavoredWenkuNovel(
        user: AuthenticatedUser,
        novelId: String,
    ) {
        val novel = wenkuMetadataRepo.get(novelId)
            ?: throwNovelNotFound()
        userRepo.deleteFavoriteWenkuNovel(
            userId = ObjectId(user.id),
            novelId = novel.id,
        )
    }
}
