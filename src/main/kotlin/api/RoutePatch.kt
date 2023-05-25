package api

import infra.web.WebNovelMetadataRepository
import infra.web.WebNovelPatchHistoryRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.None
import util.Optional
import util.Some

@Resource("/patch")
private class Patch {
    @Resource("/list")
    data class List(
        val parent: Patch = Patch(),
        val page: Int,
    )

    @Resource("/{providerId}/{novelId}")
    data class Id(
        val parent: Patch = Patch(),
        val providerId: String,
        val novelId: String,
    ) {
        @Resource("/revoke")
        data class Revoke(val parent: Id)
    }
}

fun Route.routePatch() {
    val service by inject<PatchService>()

    get<Patch.List> { loc ->
        val result = service.listPatch(
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    get<Patch.Id> { loc ->
        val result = service.getPatch(loc.providerId, loc.novelId)
        call.respondResult(result)
    }

    authenticate {
        delete<Patch.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.deletePatch(loc.providerId, loc.novelId)
            }
            call.respondResult(result)
        }

        post<Patch.Id.Revoke> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.revokePatch(loc.parent.providerId, loc.parent.novelId)
            }
            call.respondResult(result)
        }
    }
}

class PatchService(
    private val patchRepo: WebNovelPatchHistoryRepository,
    private val metadataRepo: WebNovelMetadataRepository,
) {
    @Serializable
    data class PatchHistoryPageDto(
        val total: Long,
        val items: List<PatchHistoryOutlineDto>,
    )

    @Serializable
    data class PatchHistoryOutlineDto(
        val providerId: String,
        val novelId: String,
        val titleJp: String,
        val titleZh: String?,
    )

    suspend fun listPatch(
        page: Int,
        pageSize: Int,
    ): Result<PatchHistoryPageDto> {
        val items = patchRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val total = patchRepo.count()
        return Result.success(PatchHistoryPageDto(total = total, items = items.map {
            PatchHistoryOutlineDto(
                providerId = it.providerId,
                novelId = it.novelId,
                titleJp = it.titleJp,
                titleZh = it.titleZh,
            )
        }))
    }

    @Serializable
    data class NovelPatchHistoryDto(
        val providerId: String,
        val novelId: String,
        val titleJp: String,
        val titleZh: String?,
        val patches: List<WebNovelPatchHistoryRepository.NovelPatchHistory.Patch>,
    )

    suspend fun getPatch(
        providerId: String,
        novelId: String,
    ): Result<NovelPatchHistoryDto> {
        val patch = patchRepo.findOne(providerId, novelId)
            ?: return httpNotFound("未找到")
        val dto = NovelPatchHistoryDto(
            providerId = patch.providerId,
            novelId = patch.novelId,
            titleJp = patch.titleJp,
            titleZh = patch.titleZh,
            patches = patch.patches,
        )
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
        val metadata = metadataRepo.findOne(providerId, novelId)
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
        metadata.toc.forEachIndexed { index, item ->
            if (tocMap.containsKey(item.titleJp)) {
                tocZh[index] = tocMap[item.titleJp]
            }
        }
        metadataRepo.updateZh(
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
