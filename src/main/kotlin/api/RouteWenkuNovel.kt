package api

import data.wenku.WenkuBookFileRepository
import data.wenku.WenkuBookMetadataRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId

@Serializable
@Resource("/wenku")
private class WenkuNovel {
    @Serializable
    @Resource("/list")
    data class List(
        val parent: WenkuNovel = WenkuNovel(),
        val page: Int,
        val query: String? = null,
    )

    @Serializable
    @Resource("/metadata/{bookId}")
    data class Metadata(
        val parent: WenkuNovel = WenkuNovel(),
        val bookId: String,
    )

    @Serializable
    @Resource("/episode/{bookId}")
    data class Episode(
        val parent: WenkuNovel = WenkuNovel(),
        val bookId: String,
    )
}

fun Route.routeWenkuNovel() {
    val service by inject<WenkuNovelService>()

    get<WenkuNovel.List> { loc ->
        val result = service.list(
            queryString = loc.query?.ifBlank { null },
            page = loc.page,
            pageSize = 24,
        )
        call.respondResult(result)
    }

    get<WenkuNovel.Metadata> { loc ->
        val result = service.getMetadata(loc.bookId)
        call.respondResult(result)
    }

    authenticate {
        post<WenkuNovel.Metadata> { loc ->
            val jwtUser = call.jwtUser()
            if (!jwtUser.atLeastMaintainer()) {
                call.respondResult(httpUnauthorized("只有维护者及以上才有权限执行此操作"))
            }
            val body = call.receive<WenkuNovelService.MetadataCreateBody>()
            val result = service.createMetadata(loc.bookId, body)
            call.respondResult(result)
        }

        put<WenkuNovel.Metadata> { loc ->
            val jwtUser = call.jwtUser()
            if (!jwtUser.atLeastMaintainer()) {
                call.respondResult(httpUnauthorized("只有维护者及以上才有权限执行此操作"))
            }
            val body = call.receive<WenkuNovelService.MetadataCreateBody>()
            val result = service.updateMetadata(loc.bookId, body)
            call.respondResult(result)
        }

        post<WenkuNovel.Episode> { loc ->
            val jwtUser = call.jwtUser()
            if (!jwtUser.atLeastMaintainer()) {
                call.respondResult(httpUnauthorized("只有维护者及以上才有权限执行此操作"))
            }
            val multipartData = call.receiveMultipart()
            multipartData.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName!!
                    val inputStream = part.streamProvider()
                    val result = service.createEpisodeFile(
                        loc.bookId, fileName, inputStream
                    )
                    call.respondResult(result)
                    return@forEachPart
                }
                part.dispose()
            }
        }
    }
}

class WenkuNovelService(
    private val metadataRepo: WenkuBookMetadataRepository,
    private val fileRepo: WenkuBookFileRepository,
) {
    @Serializable
    data class BookListPageDto(
        val pageNumber: Long,
        val items: List<ItemDto>,
    ) {
        @Serializable
        data class ItemDto(
            val bookId: String,
            val title: String,
            val cover: String,
            val author: String,
            val artists: String,
            val keywords: List<String>,
        )
    }

    suspend fun list(
        queryString: String?,
        page: Int,
        pageSize: Int,
    ): Result<BookListPageDto> {
        val esPage = metadataRepo.search(
            queryString = queryString,
            page = page,
            pageSize = pageSize,
        )
        val items = esPage.items.map {
            BookListPageDto.ItemDto(
                bookId = it.bookId,
                title = it.title,
                cover = it.cover,
                author = it.author,
                artists = it.artist,
                keywords = it.keywords,
            )
        }
        val dto = BookListPageDto(
            pageNumber = esPage.total / pageSize,
            items = items,
        )
        return Result.success(dto)
    }

    @Serializable
    data class MetadataDto(
        val bookId: String,
        val title: String,
        val cover: String,
        val author: String,
        val artist: String,
        val keywords: List<String>,
        val introduction: String,
        val updateAt: Long,
        val files: List<String>,
    )

    suspend fun getMetadata(
        bookId: String,
    ): Result<MetadataDto> {
        val metadataEs = metadataRepo.get(bookId)
            ?: return httpNotFound("书不存在")
        val files = fileRepo.list(bookId)
        val metadataDto = MetadataDto(
            bookId = metadataEs.bookId,
            title = metadataEs.title,
            cover = metadataEs.cover,
            author = metadataEs.author,
            artist = metadataEs.artist,
            keywords = metadataEs.keywords,
            introduction = metadataEs.introduction,
            updateAt = metadataEs.updateAt,
            files = files,
        )
        return Result.success(metadataDto)
    }

    @Serializable
    data class MetadataCreateBody(
        val bookId: String,
        val title: String,
        val cover: String,
        val coverSmall: String,
        val author: String,
        val artist: String,
        val keywords: List<String>,
        val introduction: String,
    )

    suspend fun createMetadata(
        bookId: String,
        body: MetadataCreateBody,
    ): Result<Unit> {
        val metadataEs = metadataRepo.get(bookId)
        if (metadataEs != null) {
            return httpConflict("书已经存在")
        }
        metadataRepo.index(
            WenkuBookMetadataRepository.Metadata(
                bookId = bookId,
                title = body.title,
                cover = body.cover,
                coverSmall = body.coverSmall,
                author = body.author,
                artist = body.artist,
                keywords = body.keywords,
                introduction = body.introduction,
                updateAt = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateMetadata(
        bookId: String,
        body: MetadataCreateBody,
    ): Result<Unit> {
        metadataRepo.get(bookId)
            ?: return httpNotFound("书不存在")
        metadataRepo.index(
            WenkuBookMetadataRepository.Metadata(
                bookId = bookId,
                title = body.title,
                cover = body.cover,
                coverSmall = body.coverSmall,
                author = body.author,
                artist = body.artist,
                keywords = body.keywords,
                introduction = body.introduction,
                updateAt = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
            )
        )
        return Result.success(Unit)
    }

    suspend fun createEpisodeFile(
        bookId: String,
        fileName: String,
        inputStream: InputStream,
    ): Result<Unit> {
        metadataRepo.get(bookId)
            ?: return httpNotFound("书不存在")

        val outputStream = fileRepo.createAndOpen(bookId, fileName)
            .getOrElse {
                return if (it is FileAlreadyExistsException) {
                    httpConflict("文件已经存在")
                } else {
                    httpInternalServerError(it.message)
                }
            }

        var fileTooLarge = false
        withContext(Dispatchers.IO) {
            outputStream.use { out ->
                var bytesCopied: Long = 0
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    bytesCopied += bytes
                    if (bytesCopied > 1024 * 1024 * 20) {
                        fileTooLarge = true
                        return@use
                    }
                    out.write(buffer, 0, bytes)
                    bytes = inputStream.read(buffer)
                }
            }
        }

        return if (fileTooLarge) {
            fileRepo.delete(bookId, fileName)
            httpBadRequest("文件大小不能超过20MB")
        } else {
            Result.success(Unit)
        }
    }
}
