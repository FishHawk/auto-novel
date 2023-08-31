package api

import api.dto.PageDto
import api.dto.WenkuNovelUploadHistoryDto
import infra.wenku.WenkuNovelUploadHistoryRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

@Resource("/wenku-novel-admin")
private class WenkuNovelAdminRes {
    @Resource("/upload")
    class UploadHistory(val parent: WenkuNovelAdminRes) {
        @Resource("/")
        data class List(val parent: UploadHistory, val page: Int)

        @Resource("/{id}")
        data class Id(val parent: UploadHistory, val id: String)
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
    suspend fun listUploadHistory(
        page: Int,
        pageSize: Int,
    ): Result<PageDto<WenkuNovelUploadHistoryDto>> {
        val historyPage = uploadHistoryRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dtoPage = PageDto.fromPage(historyPage, pageSize) {
            WenkuNovelUploadHistoryDto.fromDomain(it)
        }
        return Result.success(dtoPage)
    }

    suspend fun deleteUploadHistory(id: String): Result<Unit> {
        uploadHistoryRepo.delete(id)
        return Result.success(Unit)
    }
}
