package api

import data.web.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
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
    )
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
        val result = service.getPatch(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )
        call.respondResult(result)
    }

    authenticate {
        delete<Patch.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.deletePatch(
                    providerId = loc.providerId,
                    bookId = loc.bookId,
                )
            }
            call.respondResult(result)
        }
    }
}

class PatchService(
    private val patchRepo: WebBookPatchRepository,
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
        TODO()
    }
}
