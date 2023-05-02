package api

import data.wenku.WenkuBookFileRepository
import data.wenku.WenkuBookIndexRepository
import data.wenku.WenkuBookMetadataRepository
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.patch
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.io.InputStream

@Resource("/wenku")
private class WenkuNovel {
    @Resource("/list")
    data class List(
        val parent: WenkuNovel = WenkuNovel(),
        val page: Int,
        val query: String? = null,
    )

    @Resource("/{bookId}")
    class Id(
        val parent: WenkuNovel = WenkuNovel(),
        val bookId: String,
    ) {
        @Resource("/episode")
        class Episode(val parent: Id)
    }
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

    get<WenkuNovel.Id> { loc ->
        val result = service.getMetadata(loc.bookId)
        call.respondResult(result)
    }

    authenticate {
        post<WenkuNovel> {
            val result = call.requireAtLeastMaintainer {
                val body = call.receive<WenkuNovelService.MetadataCreateBody>()
                service.createMetadata(body)
            }
            call.respondResult(result)
        }

        patch<WenkuNovel.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                val body = call.receive<WenkuNovelService.MetadataCreateBody>()
                service.patchMetadata(loc.bookId, body)
            }
            call.respondResult(result)
        }

        post<WenkuNovel.Id.Episode> { loc ->
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
                        loc.parent.bookId, fileName, inputStream
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
    private val fileRepo: WenkuBookFileRepository,
    private val indexRepo: WenkuBookIndexRepository,
    private val metadataRepo: WenkuBookMetadataRepository,
) {
    @Serializable
    data class BookListPageDto(
        val pageNumber: Long,
        val items: List<ItemDto>,
    ) {
        @Serializable
        data class ItemDto(
            val id: String,
            val title: String,
            val titleZh: String,
            val cover: String,
        )
    }

    suspend fun list(
        queryString: String?,
        page: Int,
        pageSize: Int,
    ): Result<BookListPageDto> {
        val esPage = indexRepo.search(
            queryString = queryString,
            page = page,
            pageSize = pageSize,
        )
        val items = esPage.items.map {
            BookListPageDto.ItemDto(
                id = it.id,
                title = it.title,
                titleZh = it.titleZh,
                cover = it.cover,
            )
        }
        val dto = BookListPageDto(
            pageNumber = (esPage.total / pageSize) + 1,
            items = items,
        )
        return Result.success(dto)
    }

    @Serializable
    data class MetadataDto(
        val id: String,
        val title: String,
        val titleZh: String,
        val titleZhAlias: List<String>,
        val cover: String,
        val coverSmall: String,
        val authors: List<String>,
        val artists: List<String>,
        val keywords: List<String>,
        val introduction: String,
        val visited: Long,
        val files: List<String>,
    )

    suspend fun getMetadata(
        bookId: String,
    ): Result<MetadataDto> {
        val metadata = metadataRepo.findOneAndIncreaseVisited(bookId)
            ?: return httpNotFound("书不存在")
        val files = fileRepo.list(bookId)
        val metadataDto = MetadataDto(
            id = metadata.id.toHexString(),
            title = metadata.title,
            titleZh = metadata.titleZh,
            titleZhAlias = metadata.titleZhAlias,
            cover = metadata.cover,
            coverSmall = metadata.coverSmall,
            authors = metadata.authors,
            artists = metadata.artists,
            keywords = metadata.keywords,
            introduction = metadata.introduction,
            visited = metadata.visited,
            files = files,
        )
        return Result.success(metadataDto)
    }

    @Serializable
    data class MetadataCreateBody(
        val title: String,
        val titleZh: String,
        val titleZhAlias: List<String>,
        val cover: String,
        val coverSmall: String,
        val authors: List<String>,
        val artists: List<String>,
        val keywords: List<String>,
        val introduction: String,
    )

    suspend fun createMetadata(
        body: MetadataCreateBody,
    ): Result<String> {
        val bookId = metadataRepo.insertOne(
            title = body.title,
            titleZh = body.titleZh,
            titleZhAlias = body.titleZhAlias,
            cover = body.cover,
            coverSmall = body.coverSmall,
            authors = body.authors,
            artists = body.artists,
            keywords = body.keywords,
            introduction = body.introduction,
        )
        indexRepo.index(
            id = bookId,
            title = body.title,
            titleZh = body.titleZh,
            titleZhAlias = body.titleZhAlias,
            cover = body.coverSmall,
            authors = body.authors,
            artists = body.artists,
            keywords = body.keywords,
        )
        return Result.success(bookId)
    }

    suspend fun patchMetadata(
        bookId: String,
        body: MetadataCreateBody,
    ): Result<Unit> {
        metadataRepo.findOne(bookId)
            ?: return httpNotFound("书不存在")
        metadataRepo.findOneAndUpdate(
            id = bookId,
            title = body.title,
            titleZh = body.titleZh,
            titleZhAlias = body.titleZhAlias,
            cover = body.cover,
            coverSmall = body.coverSmall,
            authors = body.authors,
            artists = body.artists,
            keywords = body.keywords,
            introduction = body.introduction,
        )
        indexRepo.index(
            id = bookId,
            title = body.title,
            titleZh = body.titleZh,
            titleZhAlias = body.titleZhAlias,
            cover = body.coverSmall,
            authors = body.authors,
            artists = body.artists,
            keywords = body.keywords,
        )
        return Result.success(Unit)
    }

    suspend fun createEpisodeFile(
        bookId: String,
        fileName: String,
        inputStream: InputStream,
    ): Result<Unit> {
        metadataRepo.findOne(bookId)
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
                    if (bytesCopied > 1024 * 1024 * 40) {
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
            httpBadRequest("文件大小不能超过40MB")
        } else {
            Result.success(Unit)
        }
    }
}
