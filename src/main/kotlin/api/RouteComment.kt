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
}

fun Route.routeComment() {
    val service by inject<CommentApi>()

    get<CommentRes.List> { loc ->
        val result = service.list(loc.postId, loc.page)
        call.respondResult(result)
    }

    get<CommentRes.ListSub> { loc ->
        val result = service.listSub(loc.postId, loc.parentId, loc.page)
        call.respondResult(result)
    }

    authenticate {
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
        postId: String,
        parentId: String,
        page: Int,
    ): Result<PageDto<SubCommentDto>> {
        val commentPage = commentRepository.list(
            postId = postId,
            parentId = parentId,
            page = page,
            pageSize = 10,
        )
        val dto = PageDto.fromPage(commentPage, pageSize = 10) {
            SubCommentDto(
                id = it.id,
                createAt = it.createAt,
                username = it.username,
                receiver = it.receiver,
                content = it.content,
            )
        }
        return Result.success(dto)
    }

    suspend fun list(
        postId: String,
        page: Int,
    ): Result<PageDto<CommentDto>> {
        val commentPage = commentRepository.list(
            postId = postId,
            parentId = null,
            page = page,
            pageSize = 10,
            reverse = true,
        )
        val dto = PageDto.fromPage(commentPage, pageSize = 10) {
            val subCommentPage = commentRepository.list(
                postId = postId,
                parentId = it.id,
                page = 0,
                pageSize = 10,
            )
            CommentDto(
                id = it.id,
                createAt = it.createAt,
                username = it.username,
                content = it.content,
                pageNumber = (subCommentPage.total / 10).toInt(),
                items = subCommentPage.items.map {
                    SubCommentDto(
                        id = it.id,
                        createAt = it.createAt,
                        username = it.username,
                        receiver = it.receiver,
                        content = it.content,
                    )
                }
            )
        }
        return Result.success(dto)
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
