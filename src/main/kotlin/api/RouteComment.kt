package api

import api.dto.CommentDto
import api.dto.PageDto
import api.dto.SubCommentDto
import infra.CommentRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/comment")
private class CommentRes {
    @Resource("/list")
    class List(
        val parent: CommentRes = CommentRes(),
        val postId: String,
        val page: Int,
    )

    @Resource("/list-sub")
    class ListSub(
        val parent: CommentRes = CommentRes(),
        val postId: String,
        val parentId: String,
        val page: Int,
    )

    @Resource("/vote")
    class Vote(
        val parent: CommentRes = CommentRes(),
        val commentId: String,
        val isUpvote: Boolean,
        val isCancel: Boolean,
    )
}

fun Route.routeComment() {
    val service by inject<CommentApi>()

    authenticate(optional = true) {
        get<CommentRes.List> { loc ->
            val jwtUser = call.jwtUserOrNull()
            val result = service.list(jwtUser?.username, loc.postId, loc.page)
            call.respondResult(result)
        }

        get<CommentRes.ListSub> { loc ->
            val jwtUser = call.jwtUserOrNull()
            val result = service.listSub(jwtUser?.username, loc.postId, loc.parentId, loc.page)
            call.respondResult(result)
        }
    }
    authenticate {
        post<CommentRes.Vote> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.vote(loc.commentId, loc.isUpvote, loc.isCancel, jwtUser.username)
            call.respondResult(result)
        }

        post<CommentRes> {
            val jwtUser = call.jwtUser()
            val body = call.receive<CommentApi.CommentBody>()
            val result = service.createComment(body, jwtUser.username)
            call.respondResult(result)
        }
    }
}

class CommentApi(
    private val commentRepository: CommentRepository,
) {
    suspend fun listSub(
        viewer: String?,
        postId: String,
        parentId: String,
        page: Int,
    ): Result<PageDto<SubCommentDto>> {
        val commentPage = commentRepository.list(
            postId = postId,
            parentId = parentId,
            viewer = viewer,
            page = page,
            pageSize = 10,
        )
        val dto = PageDto.fromPage(commentPage, pageSize = 10) {
            SubCommentDto(
                id = it.id,
                createAt = it.createAt,
                username = it.username,
                receiver = it.receiver,
                upvote = it.upvote,
                downvote = it.downvote,
                viewerVote = it.viewerVote,
                content = it.content,
            )
        }
        return Result.success(dto)
    }

    suspend fun list(
        viewer: String?,
        postId: String,
        page: Int,
    ): Result<PageDto<CommentDto>> {
        val commentPage = commentRepository.list(
            postId = postId,
            parentId = null,
            viewer = viewer,
            page = page,
            pageSize = 10,
            reverse = true,
        )
        val dto = PageDto.fromPage(commentPage, pageSize = 10) {
            val subCommentPage = commentRepository.list(
                postId = postId,
                parentId = it.id,
                viewer = viewer,
                page = 0,
                pageSize = 10,
            )
            CommentDto(
                id = it.id,
                createAt = it.createAt,
                username = it.username,
                upvote = it.upvote,
                downvote = it.downvote,
                viewerVote = it.viewerVote,
                content = it.content,
                pageNumber = (subCommentPage.total / 10).toInt(),
                items = subCommentPage.items.map {
                    SubCommentDto(
                        id = it.id,
                        createAt = it.createAt,
                        username = it.username,
                        receiver = it.receiver,
                        upvote = it.upvote,
                        downvote = it.downvote,
                        viewerVote = it.viewerVote,
                        content = it.content,
                    )
                }
            )
        }
        return Result.success(dto)
    }

    suspend fun vote(
        commentId: String,
        isUpvote: Boolean,
        isCancel: Boolean,
        username: String,
    ): Result<Unit> {
        if (isUpvote) {
            if (isCancel) {
                commentRepository.cancelUpvote(commentId, username)
            } else {
                commentRepository.upvote(commentId, username)
            }
        } else {
            if (isCancel) {
                commentRepository.cancelDownvote(commentId, username)
            } else {
                commentRepository.downvote(commentId, username)
            }
        }
        return Result.success(Unit)
    }

    @Serializable
    data class CommentBody(
        val postId: String,
        val parentId: String? = null,
        val receiver: String? = null,
        val content: String,
    )

    suspend fun createComment(
        body: CommentBody,
        username: String,
    ): Result<Unit> {
        if (body.content.isBlank()) {
            return httpBadRequest("回复内容不能为空")
        }
        if (body.parentId != null) {
            if (!commentRepository.exist(body.parentId)) {
                return httpNotFound("回复的评论不存在")
            }
        }
        commentRepository.add(
            postId = body.postId,
            parentId = body.parentId,
            username = username,
            receiver = body.receiver,
            content = body.content,
        )
        return Result.success(Unit)
    }
}
