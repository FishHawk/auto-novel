package api

import api.plugins.*
import infra.common.ArticleRepository
import infra.common.CommentRepository
import infra.model.UserOutline
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject

@Resource("/comment")
private class CommentRes {
    @Resource("")
    class List(
        val parent: CommentRes,
        val site: String,
        val parentId: String? = null,
        val page: Int,
        val pageSize: Int,
    )
}

fun Route.routeComment() {
    val service by inject<CommentApi>()

    get<CommentRes.List> { loc ->
        call.tryRespond {
            service.listComment(
                postId = loc.site,
                parentId = loc.parentId,
                page = loc.page,
                pageSize = loc.pageSize,
                replyPageSize = 10,
                reverse = loc.parentId == null,
            )
        }
    }

    authenticateDb {
        rateLimit(RateLimitNames.CreateComment) {
            post<CommentRes> {
                @Serializable
                class Body(
                    val site: String,
                    val parent: String? = null,
                    val content: String,
                )

                val user = call.authenticatedUser()
                val body = call.receive<Body>()
                call.tryRespond {
                    service.createComment(
                        user = user,
                        site = body.site,
                        parent = body.parent,
                        content = body.content,
                    )
                }
            }
        }
    }
}

class CommentApi(
    private val commentRepo: CommentRepository,
    private val articleRepo: ArticleRepository,
) {
    @Serializable
    data class CommentDto(
        val id: String,
        val user: UserOutline,
        val content: String,
        val createAt: Long,
        val numReplies: Int,
        val replies: List<CommentDto>,
    )

    suspend fun listComment(
        postId: String,
        parentId: String?,
        page: Int,
        pageSize: Int,
        reverse: Boolean,
        replyPageSize: Int,
    ): PageDto<CommentDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        validatePageSize(replyPageSize, max = 20)
        return commentRepo
            .listComment(
                site = postId,
                parent = parentId?.let { ObjectId(it) },
                page = page,
                pageSize = pageSize,
                reverse = reverse,
            )
            .asDto(pageSize) {
                val replies = if (parentId == null && it.numReplies > 0) {
                    commentRepo.listComment(
                        site = postId,
                        parent = it.id,
                        page = 0,
                        pageSize = replyPageSize,
                    ).items.map {
                        CommentDto(
                            id = it.id.toHexString(),
                            user = it.user,
                            content = it.content,
                            createAt = it.createAt.epochSeconds,
                            numReplies = 0,
                            replies = emptyList()
                        )
                    }
                } else {
                    emptyList()
                }
                CommentDto(
                    id = it.id.toHexString(),
                    user = it.user,
                    content = it.content,
                    createAt = it.createAt.epochSeconds,
                    numReplies = it.numReplies,
                    replies = replies,
                )
            }
    }

    suspend fun createComment(
        user: AuthenticatedUser,
        site: String,
        parent: String?,
        content: String,
    ) {
        if (content.isBlank()) throwBadRequest("回复内容不能为空")
        if (parent != null && !commentRepo.increaseNumReplies(ObjectId(parent))) throwNotFound("回复的评论不存在")

        if (site.startsWith("article-")) {
            articleRepo.increaseNumComments(ObjectId(site.removePrefix("article-")))
        }
        commentRepo.createComment(
            site = site,
            parent = parent?.let { ObjectId(it) },
            user = ObjectId(user.id),
            content = content,
        )
    }
}
