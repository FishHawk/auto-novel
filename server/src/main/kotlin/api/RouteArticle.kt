package api

import api.plugins.*
import domain.entity.*
import infra.common.ArticleRepository
import infra.common.CommentRepository
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
import org.litote.kmongo.id.toId

@Resource("/article")
private class ArticleRes {
    @Resource("")
    class List(
        val parent: ArticleRes,
        val page: Int,
        val pageSize: Int,
        val category: ArticleCategory,
    )

    @Resource("/{id}")
    class Id(val parent: ArticleRes, val id: String) {
        @Resource("/locked")
        class Locked(val parent: Id)

        @Resource("/pinned")
        class Pinned(val parent: Id)

        @Resource("/hidden")
        class Hidden(val parent: Id)
    }
}

fun Route.routeArticle() {
    val service by inject<ArticleApi>()

    authenticateDb(optional = true) {
        get<ArticleRes.List> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.listArticle(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    category = loc.category,
                )
            }
        }

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
            val category: ArticleCategory,
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
                        category = body.category,
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
                    category = body.category,
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

        put<ArticleRes.Id.Hidden> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateArticleHidden(user = user, id = loc.parent.id, hidden = true)
            }
        }
        delete<ArticleRes.Id.Hidden> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateArticleHidden(user = user, id = loc.parent.id, hidden = false)
            }
        }
    }
}

class ArticleApi(
    private val articleRepo: ArticleRepository,
    private val commentRepo: CommentRepository,
) {
    @Serializable
    data class ArticleSimplifiedDto(
        val id: String,
        val title: String,
        val category: ArticleCategory,
        val locked: Boolean,
        val pinned: Boolean,
        val hidden: Boolean,
        val numViews: Int,
        val numComments: Int,
        val user: UserOutline,
        val createAt: Long,
        val updateAt: Long,
    )

    private fun ArticleSimplifiedWithUserReadModel.asDto(
        ignoreHidden: Boolean,
    ) =
        ArticleSimplifiedDto(
            id = id.toHexString(),
            title = if (ignoreHidden || !hidden) title else "",
            category = category,
            locked = locked,
            pinned = pinned,
            hidden = hidden,
            numViews = numViews,
            numComments = numComments,
            user = user,
            createAt = createAt.epochSeconds,
            updateAt = updateAt.epochSeconds,
        )

    suspend fun listArticle(
        user: AuthenticatedUser?,
        page: Int,
        pageSize: Int,
        category: ArticleCategory,
    ): Page<ArticleSimplifiedDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)

        val ignoreHidden = user != null && user.role atLeast User.Role.Maintainer

        return articleRepo
            .listArticleWithUser(
                page = page,
                pageSize = pageSize,
                category = category,
            )
            .map { it.asDto(ignoreHidden) }
    }

    private fun throwArticleNotFound(): Nothing =
        throwNotFound("文章不存在")

    @Serializable
    data class ArticleDto(
        val id: String,
        val title: String,
        val content: String,
        val category: ArticleCategory,
        val locked: Boolean,
        val pinned: Boolean,
        val hidden: Boolean,
        val numViews: Int,
        val numComments: Int,
        val user: UserOutline,
        val createAt: Long,
        val updateAt: Long,
    )

    private fun ArticleWithUserReadModel.asDto(
        ignoreHidden: Boolean,
    ) =
        ArticleDto(
            id = id.toHexString(),
            title = if (ignoreHidden || !hidden) title else "",
            content = if (ignoreHidden || !hidden) content else "",
            category = category,
            locked = locked,
            pinned = pinned,
            hidden = hidden,
            numViews = numViews,
            numComments = numComments,
            user = user,
            createAt = createAt.epochSeconds,
            updateAt = updateAt.epochSeconds,
        )

    suspend fun getArticle(
        user: AuthenticatedUser?,
        id: String,
    ): ArticleDto {
        val ignoreHidden = user != null && user.role atLeast User.Role.Maintainer

        val article = articleRepo.getArticleWithUser(ObjectId(id))
            ?: throwArticleNotFound()

        if (user != null) {
            articleRepo.increaseNumViews(
                userIdOrIp = user.id,
                id = article.id,
            )
        }

        return article.asDto(ignoreHidden)
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
        category: ArticleCategory,
    ): String {
        validateTitle(title)
        validateContent(content)

        val articleId = articleRepo.createArticle(
            title = title,
            content = content,
            category = category,
            userId = ObjectId(user.id),
        )
        return articleId.toHexString()
    }

    suspend fun updateArticle(
        user: AuthenticatedUser,
        id: String,
        title: String,
        content: String,
        category: ArticleCategory,
    ) {
        validateTitle(title)
        validateContent(content)

        val article = articleRepo.getArticle(ObjectId(id))
            ?: throwArticleNotFound()

        if (article.user == ObjectId(user.id).toId<Article>()) {
            user.shouldBeAtLeast(User.Role.Admin)
        }

        articleRepo.updateTitleAndContent(
            id = ObjectId(id),
            title = title,
            content = content,
            category = category,
        )
    }

    suspend fun deleteArticle(
        user: AuthenticatedUser,
        id: String,
    ) {
        user.shouldBeAtLeast(User.Role.Admin)
        val isDeleted = articleRepo.deleteArticle(
            id = ObjectId(id),
        )
        if (!isDeleted) throwArticleNotFound()
        commentRepo.deleteCommentBySite("article-${id}")
    }

    suspend fun updateArticlePinned(
        user: AuthenticatedUser,
        id: String,
        pinned: Boolean,
    ) {
        user.shouldBeAtLeast(User.Role.Admin)
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
        user.shouldBeAtLeast(User.Role.Admin)
        val isUpdated = articleRepo.updateArticleLocked(
            id = ObjectId(id),
            locked = locked,
        )
        if (!isUpdated) throwArticleNotFound()
    }

    suspend fun updateArticleHidden(
        user: AuthenticatedUser,
        id: String,
        hidden: Boolean,
    ) {
        user.shouldBeAtLeast(User.Role.Admin)
        val isUpdated = articleRepo.updateArticleHidden(
            id = ObjectId(id),
            hidden = hidden,
        )
        if (!isUpdated) throwArticleNotFound()
    }
}
