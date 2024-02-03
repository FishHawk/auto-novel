package api

import api.model.WebNovelOutlineDto
import api.model.asDto
import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import infra.user.UserRepository
import infra.model.FavoredNovelListSort
import infra.model.Page
import infra.model.UserFavored
import infra.user.UserFavoredWebRepository
import infra.web.WebNovelMetadataRepository
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

@Resource("/user/favored-web")
private class UserFavoredWebRes {
    @Resource("/{favoredId}")
    class Id(
        val parent: UserFavoredWebRes,
        val favoredId: String,
    ) {
        @Resource("")
        class List(
            val parent: Id,
            val page: Int,
            val pageSize: Int,
            val sort: FavoredNovelListSort,
        )

        @Resource("/{providerId}/{novelId}")
        class Novel(
            val parent: Id,
            val providerId: String,
            val novelId: String,
        )
    }
}

fun Route.routeUserFavoredWeb() {
    val service by inject<UserFavoredWebApi>()

    authenticateDb {
        post<UserFavoredWebRes> {
            @Serializable
            class Body(val title: String)

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.createFavored(
                    user = user,
                    title = body.title,
                )
            }
        }
        put<UserFavoredWebRes.Id> { loc ->
            @Serializable
            class Body(val title: String)

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.updateFavored(
                    user = user,
                    favoredId = loc.favoredId,
                    title = body.title,
                )
            }
        }
        delete<UserFavoredWebRes.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavored(
                    user = user,
                    favoredId = loc.favoredId,
                )
            }
        }
        get<UserFavoredWebRes.Id.List> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listFavoredNovel(
                    user = user,
                    favoredId = loc.parent.favoredId,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    sort = loc.sort,
                )
            }
        }
        put<UserFavoredWebRes.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavoredNovel(
                    user = user,
                    favoredId = loc.parent.favoredId,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                )
            }
        }
        delete<UserFavoredWebRes.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavoredNovel(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                )
            }
        }
    }
}

class UserFavoredWebApi(
    private val userRepo: UserRepository,
    private val favoredRepo: UserFavoredWebRepository,
    private val metadataRepo: WebNovelMetadataRepository,
) {
    suspend fun createFavored(
        user: AuthenticatedUser,
        title: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (title.length > 20) throwBadRequest("收藏夹标题至多为20个字符")
        if (user.favoredWeb.size >= 10) throwBadRequest("收藏夹最多只能创建10个")

        favoredRepo.updateFavored(
            userId = user.id,
            favored = user.favoredWeb + listOf(
                UserFavored(
                    id = UUID.randomUUID().toString(),
                    title = title,
                )
            )
        )
    }

    suspend fun updateFavored(
        user: AuthenticatedUser,
        favoredId: String,
        title: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (title.length > 20) throwBadRequest("收藏夹标题至多为20个字符")

        favoredRepo.updateFavored(
            userId = user.id,
            favored = user.favoredWeb.map {
                if (it.id == favoredId) it.copy(title = title) else it
            },
        )
    }

    suspend fun deleteFavored(
        user: AuthenticatedUser,
        favoredId: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (favoredId == "default") throwBadRequest("不可以删除默认收藏夹")

        favoredRepo.updateFavored(
            userId = user.id,
            favored = user.favoredWeb.filter { it.id != favoredId },
        )
    }

    suspend fun listFavoredNovel(
        user: AuthenticatedUser,
        favoredId: String,
        page: Int,
        pageSize: Int,
        sort: FavoredNovelListSort,
    ): Page<WebNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return favoredRepo
            .listFavoredNovel(
                userId = user.id,
                favoredId = favoredId,
                page = page,
                pageSize = pageSize,
                sort = sort,
            )
            .map { it.asDto() }
    }

    suspend fun updateFavoredNovel(
        user: AuthenticatedUser,
        favoredId: String,
        providerId: String,
        novelId: String,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNotFound("小说不存在")

        val total = favoredRepo.countFavoredNovelByUserId(
            userId = user.id,
            favoredId = favoredId,
        )
        if (total >= 5000) {
            throwBadRequest("收藏夹已达到上限")
        }
        favoredRepo.updateFavoredNovel(
            userId = ObjectId(user.id),
            novelId = novel.id,
            favoredId = favoredId,
            updateAt = novel.updateAt,
        )
    }

    suspend fun deleteFavoredNovel(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNotFound("小说不存在")
        favoredRepo.deleteFavoredNovel(
            userId = ObjectId(user.id),
            novelId = novel.id,
        )
    }
}
