package api

import infra.web.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/toc-merge")
private class TocMergeHistory {
    @Resource("/list")
    data class List(
        val parent: TocMergeHistory = TocMergeHistory(),
        val page: Int,
    )

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
            val result = call.requireAtLeastMaintainer {
                service.delete(id = loc.id)
            }
            call.respondResult(result)
        }
    }
}

class TocMergeHistoryService(
    private val tocMergeHistoryRepo: WebNovelTocMergeHistoryRepository,
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
            val novelId: String,
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
                novelId = it.novelId,
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
        val novelId: String,
        val tocOld: List<WebNovelService.NovelMetadataDto.TocItem>,
        val tocNew: List<WebNovelService.NovelMetadataDto.TocItem>,
        val reason: String,
    )

    suspend fun get(id: String): Result<TocMergeHistoryDto> {
        val history = tocMergeHistoryRepo.findOne(id)?.let {
            TocMergeHistoryDto(
                id = it.id.toHexString(),
                providerId = it.providerId,
                novelId = it.novelId,
                tocOld = it.tocOld.map {
                    WebNovelService.NovelMetadataDto.TocItem(
                        it.titleJp,
                        it.titleZh,
                        it.chapterId,
                    )
                },
                tocNew = it.tocNew.map {
                    WebNovelService.NovelMetadataDto.TocItem(
                        it.titleJp,
                        it.titleZh,
                        it.chapterId,
                    )
                },
                reason = it.reason,
            )
        } ?: return httpNotFound("未找到")
        return Result.success(history)
    }

    suspend fun delete(id: String): Result<Unit> {
        tocMergeHistoryRepo.deleteOne(id)
        return Result.success(Unit)
    }
}
