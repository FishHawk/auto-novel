package api

import api.model.WenkuNovelOutlineDto
import api.model.asDto
import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import infra.model.FavoredNovelListSort
import infra.model.Page
import infra.model.UserFavored
import infra.user.UserFavoredWenkuRepository
import infra.user.UserRepository
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

@Resource("/user/favored-wenku")
private class UserFavoredWenkuRes {
    @Resource("/{favoredId}")
    class Id(
        val parent: UserFavoredWenkuRes,
        val favoredId: String,
    ) {
        @Resource("")
        class List(
            val parent: Id,
            val page: Int,
            val pageSize: Int,
            val sort: FavoredNovelListSort,
        )

        @Resource("/{novelId}")
        class Novel(
            val parent: Id,
            val novelId: String,
        )
    }
}

fun Route.routeUserFavoredWenku() {
    val service by inject<UserFavoredWenkuApi>()

    authenticateDb {
        post<UserFavoredWenkuRes> {
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
        put<UserFavoredWenkuRes.Id> { loc ->
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
        delete<UserFavoredWenkuRes.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavored(
                    user = user,
                    favoredId = loc.favoredId,
                )
            }
        }
        get<UserFavoredWenkuRes.Id.List> { loc ->
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
        put<UserFavoredWenkuRes.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavoredNovel(
                    user = user,
                    favoredId = loc.parent.favoredId,
                    novelId = loc.novelId,
                )
            }
        }
        delete<UserFavoredWenkuRes.Id.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteFavoredNovel(
                    user = user,
                    novelId = loc.novelId,
                )
            }
        }
    }
}

class UserFavoredWenkuApi(
    private val userRepo: UserRepository,
    private val favoredRepo: UserFavoredWenkuRepository,
    private val metadataRepo: WenkuNovelMetadataRepository,
) {
    suspend fun createFavored(
        user: AuthenticatedUser,
        title: String,
    ) {
        val user = userRepo.getById(user.id)!!

        if (title.length > 20) throwBadRequest("收藏夹标题至多为20个字符")
        if (user.favoredWenku.size >= 10) throwBadRequest("收藏夹最多只能创建10个")

        favoredRepo.updateFavored(
            userId = user.id,
            favored = user.favoredWenku + listOf(
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
            favored = user.favoredWenku.map {
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
            favored = user.favoredWenku.filter { it.id != favoredId },
        )
    }

    suspend fun listFavoredNovel(
        user: AuthenticatedUser,
        favoredId: String,
        page: Int,
        pageSize: Int,
        sort: FavoredNovelListSort,
    ): Page<WenkuNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return favoredRepo
            .listFavoriteWenkuNovel(
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
        novelId: String,
    ) {
        val novel = metadataRepo.get(novelId)
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
        novelId: String,
    ) {
        val novel = metadataRepo.get(novelId)
            ?: throwNotFound("小说不存在")
        favoredRepo.deleteFavoredNovel(
            userId = ObjectId(user.id),
            novelId = novel.id,
        )
    }
}
