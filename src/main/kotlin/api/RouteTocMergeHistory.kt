package api

import data.web.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
@Resource("/toc-merge")
private class TocMergeHistory {
    @Serializable
    @Resource("/list")
    data class List(
        val parent: TocMergeHistory = TocMergeHistory(),
        val page: Int,
    )

    @Serializable
    @Resource("/item/{id}")
    data class Item(
        val parent: TocMergeHistory = TocMergeHistory(),
        val id: String,
    )
}

fun Route.routeTocMergeHistory() {
    val service by inject<TocMergeHistoryService>()

    get<TocMergeHistory.List> { loc ->
        val result = service.list(
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    get<TocMergeHistory.Item> { loc ->
        val result = service.get(
            id = loc.id,
        )
        call.respondResult(result)
    }

    authenticate {
        delete<TocMergeHistory.Item> { loc ->
            call.requireAtLeastMaintainer()
                ?.let { return@delete call.respondResult(it) }
            val result = service.delete(
                id = loc.id,
            )
            call.respondResult(result)
        }
    }
}

class TocMergeHistoryService(
    private val tocMergeHistoryRepo: WebBookTocMergeHistoryRepository,
) {
    @Serializable
    data class TocMergeHistoryPageDto(
        val total: Long,
        val items: List<ItemDto>,
    ) {
        @Serializable
        data class ItemDto(
            val id: String,
            val providerId: String,
            val bookId: String,
            val reason: String,
        )
    }

    suspend fun list(
        page: Int,
        pageSize: Int,
    ): Result<TocMergeHistoryPageDto> {
        val items = tocMergeHistoryRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        ).map {
            TocMergeHistoryPageDto.ItemDto(
                id = it.id.toHexString(),
                providerId = it.providerId,
                bookId = it.bookId,
                reason = it.reason,
            )
        }
        val total = tocMergeHistoryRepo.count()
        return Result.success(TocMergeHistoryPageDto(total = total, items = items))
    }

    @Serializable
    data class TocMergeHistoryDto(
        val id: String,
        val providerId: String,
        val bookId: String,
        val tocOld: List<BookTocItem>,
        val tocNew: List<BookTocItem>,
        val reason: String,
    )

    suspend fun get(id: String): Result<TocMergeHistoryDto> {
        val history = tocMergeHistoryRepo.get(id)?.let {
            TocMergeHistoryDto(
                id = it.id.toHexString(),
                providerId = it.providerId,
                bookId = it.bookId,
                tocOld = it.tocOld,
                tocNew = it.tocNew,
                reason = it.reason,
            )
        } ?: return httpNotFound("未找到")
        return Result.success(history)
    }

    suspend fun delete(id: String): Result<Unit> {
        tocMergeHistoryRepo.delete(id)
        return Result.success(Unit)
    }
}
