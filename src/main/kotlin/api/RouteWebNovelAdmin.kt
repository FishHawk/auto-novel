package api

import api.dto.*
import infra.web.WebNovelMetadataRepository
import infra.web.WebNovelPatchHistoryRepository
import infra.web.WebNovelTocMergeHistoryRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import util.None
import util.Optional
import util.Some

@Resource("/web-novel-admin")
private class WebNovelAdminRes {
    @Resource("/toc-merge")
    class TocMergeHistory(val parent: WebNovelAdminRes) {
        @Resource("/")
        class List(val parent: TocMergeHistory, val page: Int)

        @Resource("/{id}")
        class Id(val parent: TocMergeHistory, val id: String)
    }

    @Resource("/patch")
    class PatchHistory(val parent: WebNovelAdminRes) {
        @Resource("/")
        class List(val parent: PatchHistory, val page: Int)

        @Resource("/{providerId}/{novelId}")
        class Id(val parent: PatchHistory, val providerId: String, val novelId: String) {
            @Resource("/revoke")
            class Revoke(val parent: Id)
        }
    }
}

fun Route.routeWebNovelAdmin() {
    val api by inject<WebNovelAdminApi>()

    get<WebNovelAdminRes.TocMergeHistory.List> { loc ->
        val result = api.listTocMergeHistory(
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    get<WebNovelAdminRes.TocMergeHistory.Id> { loc ->
        val result = api.getTocMergeHistory(
            id = loc.id,
        )
        call.respondResult(result)
    }

    authenticate {
        delete<WebNovelAdminRes.TocMergeHistory.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.deleteTocMergeHistory(id = loc.id)
            }
            call.respondResult(result)
        }
    }

    get<WebNovelAdminRes.PatchHistory.List> { loc ->
        val result = api.listPatch(
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    get<WebNovelAdminRes.PatchHistory.Id> { loc ->
        val result = api.getPatch(loc.providerId, loc.novelId)
        call.respondResult(result)
    }

    authenticate {
        delete<WebNovelAdminRes.PatchHistory.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.deletePatch(loc.providerId, loc.novelId)
            }
            call.respondResult(result)
        }

        post<WebNovelAdminRes.PatchHistory.Id.Revoke> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.revokePatch(loc.parent.providerId, loc.parent.novelId)
            }
            call.respondResult(result)
        }
    }
}

class WebNovelAdminApi(
    private val tocMergeHistoryRepo: WebNovelTocMergeHistoryRepository,
    private val patchRepo: WebNovelPatchHistoryRepository,
    private val novelRepo: WebNovelMetadataRepository,
) {
    suspend fun listTocMergeHistory(
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

    suspend fun getTocMergeHistory(id: String): Result<WebNovelTocMergeHistoryDto> {
        val history = tocMergeHistoryRepo.get(id)?.let {
            WebNovelTocMergeHistoryDto.fromDomain(it)
        } ?: return httpNotFound("未找到")
        return Result.success(history)
    }

    suspend fun deleteTocMergeHistory(id: String): Result<Unit> {
        tocMergeHistoryRepo.delete(id)
        return Result.success(Unit)
    }

    suspend fun listPatch(
        page: Int,
        pageSize: Int,
    ): Result<PageDto<WebNovelPatchHistoryOutlineDto>> {
        val patchPage = patchRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dto = PageDto.fromPage(patchPage, pageSize) {
            WebNovelPatchHistoryOutlineDto.fromDomain(it)
        }
        return Result.success(dto)
    }

    suspend fun getPatch(
        providerId: String,
        novelId: String,
    ): Result<WebNovelPatchHistoryDto> {
        val patch = patchRepo.get(providerId, novelId)
            ?: return httpNotFound("未找到")
        val dto = WebNovelPatchHistoryDto.fromDomain(patch)
        return Result.success(dto)
    }

    suspend fun deletePatch(
        providerId: String,
        novelId: String,
    ): Result<Unit> {
        patchRepo.delete(
            providerId = providerId,
            novelId = novelId,
        )
        return Result.success(Unit)
    }

    suspend fun revokePatch(
        providerId: String,
        novelId: String,
    ): Result<Unit> {
        val patch = patchRepo.get(providerId, novelId)
            ?: return httpNotFound("未找到")
        val novel = novelRepo.get(providerId, novelId)
            ?: return httpNotFound("未找到对应小说")

        var titleZh: Optional<String?> = None
        var introductionZh: Optional<String?> = None
        val tocMap = mutableMapOf<String, String?>()
        patch.patches.reversed().forEach { p ->
            p.titleChange?.let { titleZh = Some(it.zhOld) }
            p.introductionChange?.let { introductionZh = Some(it.zhOld) }
            p.tocChange.forEach { tocMap[it.jp] = it.zhOld }
        }

        val tocZh = mutableMapOf<Int, String?>()
        novel.toc.forEachIndexed { index, item ->
            if (tocMap.containsKey(item.titleJp)) {
                tocZh[index] = tocMap[item.titleJp]
            }
        }
        novelRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            titleZh = titleZh,
            introductionZh = introductionZh,
            tocZh = tocZh,
        )
        patchRepo.delete(
            providerId = providerId,
            novelId = novelId,
        )
        return Result.success(Unit)
    }
}
