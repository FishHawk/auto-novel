package api

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
        @Resource("/")
        class List(val parent: UploadHistory, val page: Int)

        @Resource("/{id}")
        class Id(val parent: UploadHistory, val id: String)
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
}

class WenkuNovelAdminApi(
    private val uploadHistoryRepo: WenkuNovelUploadHistoryRepository,
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
}
