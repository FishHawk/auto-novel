package api

import api.plugins.*
import infra.common.ArticleRepository
import infra.model.UserOutline
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
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
        val pageSize: Int,
    )

    @Resource("/{id}")
    class Id(val parent: ArticleRes, val id: String) {
        @Resource("/locked")
        class Locked(val parent: Id)

        @Resource("/pinned")
        class Pinned(val parent: Id)
    }
}

fun Route.routeArticle() {
    val service by inject<ArticleApi>()

    get<ArticleRes.List> { loc ->
        call.tryRespond {
            service.listArticle(page = loc.page, pageSize = loc.pageSize)
        }
    }

    authenticateDb(optional = true) {
        get<ArticleRes.Id> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.getArticle(user = user, id = loc.id)
            }
        }
    }

    authenticateDb {
        @Serializable
        class ArticleBody(
            val title: String,
            val content: String,
        )
        rateLimit(RateLimitNames.CreateArticle) {
            post<ArticleRes> {
                val user = call.authenticatedUser()
                val body = call.receive<ArticleBody>()
                call.tryRespond {
                    service.createArticle(
                        user = user,
                        title = body.title,
                        content = body.content,
                    )
                }
            }
        }
        put<ArticleRes.Id> { loc ->
            val user = call.authenticatedUser()
            val body = call.receive<ArticleBody>()
            call.tryRespond {
                service.updateArticle(
                    user = user,
                    id = loc.id,
                    title = body.title,
                    content = body.content,
                )
            }
        }
        delete<ArticleRes.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteArticle(user = user, id = loc.id)
            }
        }

        put<ArticleRes.Id.Locked> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateArticleLocked(user = user, id = loc.parent.id, locked = true)
            }
        }
        delete<ArticleRes.Id.Locked> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateArticleLocked(user = user, id = loc.parent.id, locked = false)
            }
        }

        put<ArticleRes.Id.Pinned> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateArticlePinned(user = user, id = loc.parent.id, pinned = true)
            }
        }
        delete<ArticleRes.Id.Pinned> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateArticlePinned(user = user, id = loc.parent.id, pinned = false)
            }
        }
    }
}

class ArticleApi(
    private val articleRepo: ArticleRepository,
) {
    @Serializable
    data class ArticleOutlineDto(
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

    suspend fun listArticle(
        page: Int,
        pageSize: Int,
    ): PageDto<ArticleOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return articleRepo
            .listArticle(
                page = page,
                pageSize = pageSize,
            )
            .asDto(pageSize) {
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
    }

    private fun throwArticleNotFound(): Nothing =
        throwNotFound("帖子不存在")

    @Serializable
    data class ArticleDto(
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

    suspend fun getArticle(
        user: AuthenticatedUser?,
        id: String,
    ): ArticleDto {
        val article = articleRepo.getArticle(ObjectId(id))
            ?: throwArticleNotFound()

        if (user != null) {
            articleRepo.increaseNumViews(
                userIdOrIp = user.id,
                id = article.id,
            )
        }

        return article.let {
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
    }

    private fun validateTitle(title: String) {
        if (title.length < 2) {
            throwBadRequest("标题长度不能少于2个字符")
        } else if (title.length > 80) {
            throwBadRequest("标题长度不能超过80个字符")
        }
    }

    private fun validateContent(content: String) {
        if (content.length < 2) {
            throwBadRequest("内容长度不能少于2个字符")
        } else if (content.length > 20_000) {
            throwBadRequest("内容长度不能超过2万个字符")
        }
    }

    suspend fun createArticle(
        user: AuthenticatedUser,
        title: String,
        content: String,
    ): String {
        validateTitle(title)
        validateContent(content)

        val articleId = articleRepo.createArticle(
            title = title,
            content = content,
            userId = ObjectId(user.id),
        )
        return articleId.toHexString()
    }

    suspend fun updateArticle(
        user: AuthenticatedUser,
        id: String,
        title: String,
        content: String,
    ) {
        validateTitle(title)
        validateContent(content)

        if (!articleRepo.isArticleBelongUser(ObjectId(id), ObjectId(user.id)))
            throwUnauthorized("没有权限修改帖子")

        articleRepo.updateArticleTitleAndContent(
            id = ObjectId(id),
            title = title,
            content = content,
        )
    }

    suspend fun deleteArticle(
        user: AuthenticatedUser,
        id: String,
    ) {
        user.shouldBeAtLeastMaintainer()
        val isDeleted = articleRepo.deleteArticle(
            id = ObjectId(id),
        )
        if (!isDeleted) throwArticleNotFound()
    }

    suspend fun updateArticlePinned(
        user: AuthenticatedUser,
        id: String,
        pinned: Boolean,
    ) {
        user.shouldBeAtLeastMaintainer()
        val isUpdated = articleRepo.updateArticlePinned(
            id = ObjectId(id),
            pinned = pinned,
        )
        if (!isUpdated) throwArticleNotFound()
    }

    suspend fun updateArticleLocked(
        user: AuthenticatedUser,
        id: String,
        locked: Boolean,
    ) {
        user.shouldBeAtLeastMaintainer()
        val isUpdated = articleRepo.updateArticleLocked(
            id = ObjectId(id),
            locked = locked,
        )
        if (!isUpdated) throwArticleNotFound()
    }
}
