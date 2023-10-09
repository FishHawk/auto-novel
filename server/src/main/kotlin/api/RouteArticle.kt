package api

import infra.common.ArticleRepository
import infra.common.UserRepository
import infra.model.UserOutline
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject

@Resource("/article")
private class ArticleRes {
    @Resource("")
    class List(
        val parent: ArticleRes,
        val page: Int,
    )

    @Resource("/{id}")
    class Id(
        val parent: ArticleRes,
        val id: String,
    ) {
        @Resource("/locked")
        class Locked(val parent: Id)

        @Resource("/pinned")
        class Pinned(val parent: Id)
    }
}

fun Route.routeArticle() {
    val service by inject<ArticleApi>()

    get<ArticleRes.List> { loc ->
        val result = service.listArticle(page = loc.page, pageSize = 20)
        call.respondResult(result)
    }
    get<ArticleRes.Id> { loc ->
        val result = service.getArticle(id = loc.id)
        call.respondResult(result)
    }

    authenticate {
        @Serializable
        class ArticleBody(val title: String, val content: String)

        post<ArticleRes> {
            val jwtUser = call.jwtUser()
            val body = call.receive<ArticleBody>()
            val result = service.createArticle(
                title = body.title,
                content = body.content,
                username = jwtUser.username,
            )
            call.respondResult(result)
        }
        put<ArticleRes.Id> { loc ->
            val jwtUser = call.jwtUser()
            val body = call.receive<ArticleBody>()
            val result = service.updateArticle(
                id = loc.id,
                title = body.title,
                content = body.content,
                username = jwtUser.username,
            )
            call.respondResult(result)
        }
        delete<ArticleRes.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.deleteArticle(id = loc.id)
            }
            call.respondResult(result)
        }

        put<ArticleRes.Id.Locked> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.updateArticleLocked(id = loc.parent.id, locked = true)
            }
            call.respondResult(result)
        }
        delete<ArticleRes.Id.Locked> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.updateArticleLocked(id = loc.parent.id, locked = false)
            }
            call.respondResult(result)
        }

        put<ArticleRes.Id.Pinned> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.updateArticlePinned(id = loc.parent.id, pinned = true)
            }
            call.respondResult(result)
        }
        delete<ArticleRes.Id.Pinned> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.updateArticlePinned(id = loc.parent.id, pinned = false)
            }
            call.respondResult(result)
        }
    }
}

class ArticleApi(
    private val articleRepo: ArticleRepository,
    private val userRepo: UserRepository,
) {
    @Serializable
    class ArticleOutlineDto(
        val id: String,
        val title: String,
        val locked: Boolean,
        val pinned: Boolean,
        val numViews: Int,
        val numComments: Int,
        val user: UserOutline,
        val createAt: Long,
        val updateAt: Long,
    )

    suspend fun listArticle(page: Int, pageSize: Int): Result<PageDto<ArticleOutlineDto>> {
        val list = articleRepo
            .listArticle(
                page = page.coerceAtLeast(0),
                pageSize = pageSize.coerceAtLeast(0),
            )
        val dto = PageDto.fromPage(list, pageSize = pageSize) {
            ArticleOutlineDto(
                id = it.id.toHexString(),
                title = it.title,
                locked = it.locked,
                pinned = it.pinned,
                numViews = it.numViews,
                numComments = it.numComments,
                user = it.user,
                createAt = it.createAt.epochSeconds,
                updateAt = it.updateAt.epochSeconds,
            )

        }
        return Result.success(dto)
    }

    @Serializable
    class ArticleDto(
        val id: String,
        val title: String,
        val content: String,
        val locked: Boolean,
        val pinned: Boolean,
        val numViews: Int,
        val numComments: Int,
        val user: UserOutline,
        val createAt: Long,
        val updateAt: Long,
    )

    suspend fun getArticle(id: String): Result<ArticleDto> {
        val article = articleRepo.getArticle(ObjectId(id))
            ?: return httpNotFound("帖子不存在")
        articleRepo.increaseNumViews(article.id)

        val dto = article.let {
            ArticleDto(
                id = it.id.toHexString(),
                title = it.title,
                content = it.content,
                locked = it.locked,
                pinned = it.pinned,
                numViews = it.numViews,
                numComments = it.numComments,
                user = it.user,
                createAt = it.createAt.epochSeconds,
                updateAt = it.updateAt.epochSeconds,
            )
        }
        return Result.success(dto)
    }

    private fun <T> validateTitleAndContent(
        title: String,
        content: String,
    ): Result<T>? {
        if (title.length < 2) {
            return httpBadRequest("标题长度不能少于2个字符")
        } else if (title.length > 80) {
            return httpBadRequest("标题长度不能超过80个字符")
        }
        if (content.length < 2) {
            return httpBadRequest("内容长度不能少于2个字符")
        } else if (content.length > 20_000) {
            return httpBadRequest("内容长度不能超过2万个字符")
        }
        return null
    }

    suspend fun createArticle(
        title: String,
        content: String,
        username: String,
    ): Result<String> {
        validateTitleAndContent<String>(title = title, content = content)
            ?.let { return it }

        val userId = userRepo.getUserIdByUsername(username)
        val id = articleRepo.createArticle(
            title = title,
            content = content,
            userId = userId,
        )
        return Result.success(id.toHexString())
    }

    suspend fun updateArticle(
        id: String,
        title: String,
        content: String,
        username: String,
    ): Result<Unit> {
        validateTitleAndContent<Unit>(title = title, content = content)
            ?.let { return it }

        val userId = userRepo.getUserIdByUsername(username)
        if (!articleRepo.isArticleBelongUser(ObjectId(id), userId))
            return httpUnauthorized("没有权限修改帖子")
        articleRepo.updateArticleTitleAndContent(
            id = ObjectId(id),
            title = title,
            content = content,
        )
        return Result.success(Unit)
    }

    suspend fun deleteArticle(id: String): Result<Unit> {
        val isDeleted = articleRepo.deleteArticle(
            id = ObjectId(id),
        )
        if (!isDeleted) {
            return httpNotFound("帖子不存在")
        }
        return Result.success(Unit)
    }

    suspend fun updateArticlePinned(
        id: String,
        pinned: Boolean,
    ): Result<Unit> {
        val isUpdated = articleRepo.updateArticlePinned(
            id = ObjectId(id),
            pinned = pinned,
        )
        if (!isUpdated) {
            return httpNotFound("帖子不存在")
        }
        return Result.success(Unit)
    }

    suspend fun updateArticleLocked(
        id: String,
        locked: Boolean,
    ): Result<Unit> {
        val isUpdated = articleRepo.updateArticleLocked(
            id = ObjectId(id),
            locked = locked,
        )
        if (!isUpdated) {
            return httpNotFound("帖子不存在")
        }
        return Result.success(Unit)
    }
}
