package api

import data.web.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/patch")
private class Patch {
    @Resource("/list")
    data class List(
        val parent: Patch = Patch(),
        val page: Int,
    )

    @Resource("/{providerId}/{bookId}")
    data class Id(
        val parent: Patch = Patch(),
        val providerId: String,
        val bookId: String,
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
        val result = service.getPatch(loc.providerId, loc.bookId)
        call.respondResult(result)
    }

    authenticate {
        delete<Patch.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.deletePatch(loc.providerId, loc.bookId)
            }
            call.respondResult(result)
        }

        post<Patch.Id.Revoke> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.revokePatch(loc.parent.providerId, loc.parent.bookId)
            }
            call.respondResult(result)
        }
    }
}

class PatchService(
    private val patchRepo: WebBookPatchRepository,
    private val metadataRepo: WebBookMetadataRepository,
) {
    @Serializable
    data class PatchPageDto(
        val total: Long,
        val items: List<BookPatchOutline>,
    )

    suspend fun listPatch(
        page: Int,
        pageSize: Int,
    ): Result<PatchPageDto> {
        val items = patchRepo.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val total = patchRepo.count()
        return Result.success(PatchPageDto(total = total, items = items))
    }

    suspend fun getPatch(
        providerId: String,
        bookId: String,
    ): Result<BookPatch> {
        val patch = patchRepo.get(providerId, bookId)
            ?: return httpNotFound("未找到")
        return Result.success(patch)
    }

    suspend fun deletePatch(
        providerId: String,
        bookId: String,
    ): Result<Unit> {
        patchRepo.deletePatch(
            providerId = providerId,
            bookId = bookId,
        )
        return Result.success(Unit)
    }

    suspend fun revokePatch(
        providerId: String,
        bookId: String,
    ): Result<Unit> {
        val patch = patchRepo.get(providerId, bookId)
            ?: return httpNotFound("未找到")
        val metadata = metadataRepo.getLocal(providerId, bookId)
            ?: return httpNotFound("未找到对应小说")

        var title: String? = null
        var introduction: String? = null
        val tocMap = mutableMapOf<String, String?>()
        patch.patches.reversed().forEach { p ->
            p.titleChange?.let { title = it.zhOld }
            p.introductionChange?.let { introduction = it.zhOld }
            p.tocChange.forEach { tocMap[it.jp] = it.zhOld }
        }
        val toc = metadata.toc.mapIndexedNotNull { index, toc ->
            tocMap[toc.titleJp]?.let { index to it }
        }.toMap()
//        metadataRepo.updateZh()
        return Result.success(Unit)
    }
}
