package api

import data.web.BookPatch
import data.web.BookPatchOutline
import data.web.BookPatchRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
@Resource("/patch")
private class Patch {
    @Serializable
    @Resource("/list")
    data class List(
        val parent: Patch = Patch(),
        val page: Int,
    )

    @Serializable
    @Resource("/self/{providerId}/{bookId}")
    data class Self(
        val parent: Patch = Patch(),
        val providerId: String,
        val bookId: String,
    )
}

fun Route.routePatch() {
    val service by inject<PatchService>()

    get<Patch.List> { loc ->
        val result = service.list(
            page = loc.page,
            pageSize = 10,
        )
        call.respondResult(result)
    }

    get<Patch.Self> { loc ->
        val result = service.get(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )
        call.respondResult(result)
    }

    authenticate {
        delete<Patch.Self> { loc ->
            if (!call.jwtUser().atLeastMaintainer()) {
                call.respondResult(httpUnauthorized("只有维护者及以上才有权限执行此操作"))
            }
            val result = service.deletePatch(
                providerId = loc.providerId,
                bookId = loc.bookId,
            )
            call.respondResult(result)
        }
    }
}

class PatchService(
    private val bookPatchRepository: BookPatchRepository,
) {
    @Serializable
    data class BookPageDto(
        val total: Long,
        val items: List<BookPatchOutline>,
    )

    suspend fun list(
        page: Int,
        pageSize: Int,
    ): Result<BookPageDto> {
        val items = bookPatchRepository.list(
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val total = bookPatchRepository.count()
        return Result.success(BookPageDto(total = total, items = items))
    }

    suspend fun get(
        providerId: String,
        bookId: String,
    ): Result<BookPatch> {
        val patch = bookPatchRepository.get(providerId, bookId)
            ?: return httpNotFound("未找到")
        return Result.success(patch)
    }

    suspend fun deletePatch(
        providerId: String,
        bookId: String,
    ): Result<Unit> {
        bookPatchRepository.deletePatch(
            providerId = providerId,
            bookId = bookId,
        )

        return Result.success(Unit)
    }
}
