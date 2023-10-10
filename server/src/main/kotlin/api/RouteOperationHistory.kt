package api

import infra.common.OperationHistoryRepository
import infra.model.Operation
import infra.model.UserOutline
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/operation-history")
private class OperationHistoryRes {
    @Resource("")
    class List(
        val parent: OperationHistoryRes,
        val page: Int,
        val pageSize: Int = 10,
        val type: String,
    )

    @Resource("/{id}")
    class Id(
        val parent: OperationHistoryRes,
        val id: String,
    )

    // Wait for deprecate
    @Resource("/toc-merge")
    class TocMergeHistory(val parent: OperationHistoryRes) {
        @Resource("/")
        class List(val parent: TocMergeHistory, val page: Int)

        @Resource("/{id}")
        class Id(val parent: TocMergeHistory, val id: String)
    }
}

fun Route.routeOperationHistory() {
    val api by inject<OperationHistoryApi>()

    authenticate {
        get<OperationHistoryRes.List> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.listOperationHistory(
                    page = loc.page.coerceAtLeast(0),
                    pageSize = loc.pageSize.coerceIn(1, 100),
                    type = loc.type,
                )
            }
            call.respondResult(result)
        }

        delete<OperationHistoryRes.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.deleteOperationHistory(id = loc.id)
            }
            call.respondResult(result)
        }

        // Wait for deprecate
        get<OperationHistoryRes.TocMergeHistory.List> { loc ->
            val result = api.listTocMergeHistory(
                page = loc.page,
                pageSize = 10,
            )
            call.respondResult(result)
        }
        delete<OperationHistoryRes.TocMergeHistory.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.deleteTocMergeHistory(id = loc.id)
            }
            call.respondResult(result)
        }
    }
}

class OperationHistoryApi(
    private val operationHistoryRepo: OperationHistoryRepository,
) {
    @Serializable
    class OperationHistoryDto(
        val id: String,
        val operator: UserOutline,
        val operation: Operation,
        val createAt: Long,
    )

    suspend fun listOperationHistory(
        page: Int,
        pageSize: Int,
        type: String,
    ): Result<PageDto<OperationHistoryDto>> {
        val historyPage = operationHistoryRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
            type = type,
        )
        val dtoPage = PageDto.fromPage(historyPage, pageSize) {
            OperationHistoryDto(
                id = it.id.toHexString(),
                operator = it.operator,
                operation = it.operation,
                createAt = it.createAt.epochSeconds,
            )
        }
        return Result.success(dtoPage)
    }

    suspend fun deleteOperationHistory(id: String): Result<Unit> {
        operationHistoryRepo.delete(id)
        return Result.success(Unit)
    }

    // Wait for deprecate
    suspend fun listTocMergeHistory(
        page: Int,
        pageSize: Int,
    ): Result<PageDto<TocMergeHistoryDto>> {
        val historyPage = operationHistoryRepo.listMergeHistory(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dtoPage = PageDto.fromPage(historyPage, pageSize) {
            TocMergeHistoryDto(
                id = it.id.toHexString(),
                providerId = it.providerId,
                novelId = it.novelId,
                tocOld = it.tocOld.map { WebNovelApi.NovelTocItemDto.fromDomain(it) },
                tocNew = it.tocNew.map { WebNovelApi.NovelTocItemDto.fromDomain(it) },
                reason = it.reason,
            )
        }
        return Result.success(dtoPage)
    }

    @Serializable
    class TocMergeHistoryDto(
        val id: String,
        val providerId: String,
        val novelId: String,
        val tocOld: List<WebNovelApi.NovelTocItemDto>,
        val tocNew: List<WebNovelApi.NovelTocItemDto>,
        val reason: String,
    )

    suspend fun deleteTocMergeHistory(id: String): Result<Unit> {
        operationHistoryRepo.deleteMergeHistory(id)
        return Result.success(Unit)
    }
}
