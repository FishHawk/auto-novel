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
}
