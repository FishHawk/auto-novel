package api

import infra.model.WenkuNovelEditHistory
import infra.wenku.WenkuNovelEditHistoryRepository
import infra.wenku.WenkuNovelUploadHistoryRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/wenku-novel-admin")
private class WenkuNovelAdminRes {
    @Resource("/upload")
    class UploadHistory(val parent: WenkuNovelAdminRes) {
        @Resource("")
        class List(val parent: UploadHistory, val page: Int)

        @Resource("/{id}")
        class Id(val parent: UploadHistory, val id: String)
    }

    @Resource("/edit")
    class EditHistory(val parent: WenkuNovelAdminRes) {
        @Resource("")
        class List(val parent: EditHistory, val page: Int)

        @Resource("/{id}")
        class Id(val parent: EditHistory, val id: String)
    }
}


fun Route.routeWenkuNovelAdmin() {
    val api by inject<WenkuNovelAdminApi>()

    get<WenkuNovelAdminRes.UploadHistory.List> { loc ->
        val result = api.listUploadHistory(
            page = loc.page,
            pageSize = 100,
        )
        call.respondResult(result)
    }

    authenticate {
        delete<WenkuNovelAdminRes.UploadHistory.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.deleteUploadHistory(id = loc.id)
            }
            call.respondResult(result)
        }
    }

    get<WenkuNovelAdminRes.EditHistory.List> { loc ->
        val result = api.listEditHistory(
            page = loc.page,
            pageSize = 100,
        )
        call.respondResult(result)
    }

    authenticate {
        delete<WenkuNovelAdminRes.EditHistory.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.deleteEditHistory(id = loc.id)
            }
            call.respondResult(result)
        }
    }
}

class WenkuNovelAdminApi(
    private val uploadHistoryRepo: WenkuNovelUploadHistoryRepository,
    private val editHistoryRepo: WenkuNovelEditHistoryRepository,
) {
    @Serializable
    class UploadHistoryDto(
        val id: String,
        val novelId: String,
        val volumeId: String,
        val uploader: String,
        val createAt: Long,
    )

    suspend fun listUploadHistory(
        page: Int,
        pageSize: Int,
    ): Result<PageDto<UploadHistoryDto>> {
        val historyPage = uploadHistoryRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dtoPage = PageDto.fromPage(historyPage, pageSize) {
            UploadHistoryDto(
                id = it.id.toHexString(),
                novelId = it.novelId,
                volumeId = it.volumeId,
                uploader = it.uploader,
                createAt = it.createAt.epochSeconds,
            )
        }
        return Result.success(dtoPage)
    }

    suspend fun deleteUploadHistory(id: String): Result<Unit> {
        uploadHistoryRepo.delete(id)
        return Result.success(Unit)
    }

    @Serializable
    class EditHistoryDto(
        val id: String,
        val novelId: String,
        val operator: String,
        val old: WenkuNovelEditHistory.Data?,
        val new: WenkuNovelEditHistory.Data,
        val createAt: Long,
    )

    suspend fun listEditHistory(
        page: Int,
        pageSize: Int,
    ): Result<PageDto<EditHistoryDto>> {
        val historyPage = editHistoryRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dtoPage = PageDto.fromPage(historyPage, pageSize) {
            EditHistoryDto(
                id = it.id.toHexString(),
                novelId = it.novelId,
                operator = it.operator,
                old = it.old,
                new = it.new,
                createAt = it.createAt.epochSeconds,
            )
        }
        return Result.success(dtoPage)
    }

    suspend fun deleteEditHistory(id: String): Result<Unit> {
        editHistoryRepo.delete(id)
        return Result.success(Unit)
    }
}
