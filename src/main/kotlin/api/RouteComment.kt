package api

import infra.ArticleRepository
import infra.CommentRepository
import infra.UserRepository
import infra.model.UserOutline
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject

@Resource("/comment")
private class CommentRes {
    @Resource("/list")
    class List(
        val parent: CommentRes = CommentRes(),
        val site: String,
        val page: Int,
    )

    @Resource("/list-sub")
    class ListSub(
        val parent: CommentRes = CommentRes(),
        val site: String,
        val parentId: String,
        val page: Int,
    )
}

fun Route.routeComment() {
    val service by inject<CommentApi>()

    get<CommentRes.List> { loc ->
        val result = service.listCommentWithReply(loc.site, loc.page, 10, 10)
        call.respondResult(result)
    }

    get<CommentRes.ListSub> { loc ->
        val result = service.listReplyComment(loc.site, loc.parentId, loc.page, 10)
        call.respondResult(result)
    }

    authenticate {
        post<CommentRes> {
            @Serializable
            class Body(
                val site: String,
                val parent: String? = null,
                val content: String,
            )

            val jwtUser = call.jwtUser()
            val body = call.receive<Body>()
            val result = service.createComment(
                site = body.site,
                parent = body.parent,
                username = jwtUser.username,
                content = body.content
            )
            call.respondResult(result)
        }
    }
}

class CommentApi(
    private val commentRepo: CommentRepository,
    private val userRepo: UserRepository,
    private val articleRepo: ArticleRepository,
) {
    @Serializable
    class CommentDto(
        val id: String,
        val user: UserOutline,
        val content: String,
        val createAt: Long,
        val numReplies: Int,
        val replies: List<CommentDto>,
    )

    suspend fun listReplyComment(
        site: String,
        parent: String,
        page: Int,
        pageSize: Int,
    ): Result<PageDto<CommentDto>> {
        val commentPage = commentRepo.listComment(
            site = site,
            parent = ObjectId(parent),
            page = page,
            pageSize = pageSize,
        )
        val dto = PageDto.fromPage(commentPage, pageSize = 10) {
            CommentDto(
                id = it.id.toHexString(),
                user = it.user,
                content = it.content,
                createAt = it.createAt.epochSeconds,
                numReplies = it.numReplies,
                replies = emptyList(),
            )
        }
        return Result.success(dto)
    }

    suspend fun listCommentWithReply(
        postId: String,
        page: Int,
        pageSize: Int,
        replyPageSize: Int,
    ): Result<PageDto<CommentDto>> {
        val commentPage = commentRepo.listComment(
            site = postId,
            parent = null,
            page = page,
            pageSize = pageSize,
            reverse = true,
        )
        val dto = PageDto.fromPage(commentPage, pageSize = 10) {
            val replies =
                if (it.numReplies > 0) {
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
        return Result.success(dto)
    }

    suspend fun createComment(
        site: String,
        parent: String?,
        username: String,
        content: String,
    ): Result<Unit> {
        if (content.isBlank()) {
            return httpBadRequest("回复内容不能为空")
        }
        if (parent != null && !commentRepo.increaseNumReplies(ObjectId(parent))) {
            return httpNotFound("回复的评论不存在")
        }
        val user = userRepo.getUserIdByUsername(username)
        if (site.startsWith("article-")) {
            articleRepo.increaseNumComments(ObjectId(site.removePrefix("article-")))
        }
        commentRepo.createComment(
            site = site,
            parent = parent?.let { ObjectId(it) },
            user = user,
            content = content,
        )
        return Result.success(Unit)
    }
}
