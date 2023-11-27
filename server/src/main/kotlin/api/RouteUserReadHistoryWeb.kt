package api

import api.model.WebNovelOutlineDto
import api.model.asDto
import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import infra.user.UserReadHistoryWebRepository
import infra.web.WebNovelMetadataRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject


@Resource("/user/read-history")
private class UserReadHistoryWebRes {
    @Resource("")
    class List(
        val parent: UserReadHistoryWebRes,
        val page: Int,
        val pageSize: Int,
    )

    @Resource("/{providerId}/{novelId}")
    class Novel(
        val parent: UserReadHistoryWebRes,
        val providerId: String,
        val novelId: String,
    )
}

fun Route.routeUserReadHistoryWeb() {
    val service by inject<UserReadHistoryWebApi>()

    authenticateDb {
        get<UserReadHistoryWebRes.List> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listReadHistory(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                )
            }
        }
        put<UserReadHistoryWebRes.Novel> { loc ->
            val user = call.authenticatedUser()
            val chapterId = call.receive<String>()
            call.tryRespond {
                service.updateReadHistory(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                    chapterId = chapterId,
                )
            }
        }
        delete<UserReadHistoryWebRes.Novel> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteReadHistory(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                )
            }
        }
    }
}

class UserReadHistoryWebApi(
    private val historyRepo: UserReadHistoryWebRepository,
    private val metadataRepo: WebNovelMetadataRepository,
) {
    suspend fun listReadHistory(
        user: AuthenticatedUser,
        page: Int,
        pageSize: Int,
    ): PageDto<WebNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return historyRepo
            .listReaderHistory(
                userId = user.id,
                page = page,
                pageSize = pageSize,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun updateReadHistory(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        chapterId: String,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNotFound("小说不存在")
        historyRepo.updateReadHistory(
            userId = ObjectId(user.id),
            novelId = novel.id,
            chapterId = chapterId,
        )
    }

    suspend fun deleteReadHistory(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNotFound("小说不存在")
        historyRepo.deleteReadHistory(
            userId = ObjectId(user.id),
            novelId = novel.id,
        )
    }
}
