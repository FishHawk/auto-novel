package api

import api.dto.PageDto
import api.dto.WebNovelPatchHistoryDto
import api.dto.WebNovelPatchHistoryOutlineDto
import infra.web.WebNovelPatchHistoryRepository
import infra.web.WebNovelMetadataRepository
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

@Resource("/patch")
private class WebNovelPatchRes {
    @Resource("/list")
    data class List(
        val parent: WebNovelPatchRes,
        val page: Int,
    )

    @Resource("/{providerId}/{novelId}")
    data class Id(
        val parent: WebNovelPatchRes,
        val providerId: String,
        val novelId: String,
    ) {
        @Resource("/revoke")
        data class Revoke(val parent: Id)
    }
}

fun Route.routePatch() {
    val api by inject<WebNovelPatchApi>()

    get<WebNovelPatchRes.List> { loc ->
        val result = api.listPatch(
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    get<WebNovelPatchRes.Id> { loc ->
        val result = api.getPatch(loc.providerId, loc.novelId)
        call.respondResult(result)
    }

    authenticate {
        delete<WebNovelPatchRes.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.deletePatch(loc.providerId, loc.novelId)
            }
            call.respondResult(result)
        }

        post<WebNovelPatchRes.Id.Revoke> { loc ->
            val result = call.requireAtLeastMaintainer {
                api.revokePatch(loc.parent.providerId, loc.parent.novelId)
            }
            call.respondResult(result)
        }
    }
}

class WebNovelPatchApi(
    private val patchRepo: WebNovelPatchHistoryRepository,
    private val novelRepo: WebNovelMetadataRepository,
) {
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
        val patch = patchRepo.findOne(providerId, novelId)
            ?: return httpNotFound("未找到")
        val dto = WebNovelPatchHistoryDto.fromDomain(patch)
        return Result.success(dto)
    }

    suspend fun deletePatch(
        providerId: String,
        novelId: String,
    ): Result<Unit> {
        patchRepo.deleteOne(
            providerId = providerId,
            novelId = novelId,
        )
        return Result.success(Unit)
    }

    suspend fun revokePatch(
        providerId: String,
        novelId: String,
    ): Result<Unit> {
        val patch = patchRepo.findOne(providerId, novelId)
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
        novelRepo.updateZh(
            providerId = providerId,
            novelId = novelId,
            titleZh = titleZh,
            introductionZh = introductionZh,
            glossary = None,
            tocZh = tocZh,
        )
        patchRepo.deleteOne(
            providerId = providerId,
            novelId = novelId,
        )
        return Result.success(Unit)
    }
}
