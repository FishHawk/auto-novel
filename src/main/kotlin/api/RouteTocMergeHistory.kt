package api

import api.dto.*
import infra.web.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

@Resource("/toc-merge")
private class TocMergeHistoryRes {
    @Resource("/list")
    data class List(
        val parent: TocMergeHistoryRes,
        val page: Int,
    )

    @Resource("/item/{id}")
    data class Item(
        val parent: TocMergeHistoryRes,
        val id: String,
    )
}

fun Route.routeTocMergeHistory() {
    val api by inject<TocMergeHistoryApi>()

    get<TocMergeHistoryRes.List> { loc ->
        val result = api.list(
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    get<TocMergeHistoryRes.Item> { loc ->
        val result = api.get(
            id = loc.id,
        )
        call.respondResult(result)
    }

    authenticate {
        delete<TocMergeHistoryRes.Item> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.delete(id = loc.id)
            }
            call.respondResult(result)
        }
    }
}

class TocMergeHistoryApi(
    private val tocMergeHistoryRepo: WebNovelTocMergeHistoryRepository,
) {
    suspend fun list(
        page: Int,
        pageSize: Int,
    ): Result<PageDto<WebNovelTocMergeHistoryOutlineDto>> {
        val historyPage = tocMergeHistoryRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dtoPage = PageDto.fromPage(historyPage, pageSize) {
            WebNovelTocMergeHistoryOutlineDto.fromDomain(it)
        }
        return Result.success(dtoPage)
    }

    suspend fun get(id: String): Result<WebNovelTocMergeHistoryDto> {
        val history = tocMergeHistoryRepo.get(id)?.let {
            WebNovelTocMergeHistoryDto.fromDomain(it)
        } ?: return httpNotFound("未找到")
        return Result.success(history)
    }

    suspend fun delete(id: String): Result<Unit> {
        tocMergeHistoryRepo.delete(id)
        return Result.success(Unit)
    }
}
