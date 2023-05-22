package api

import data.UserRepository
import data.web.BookFileLang
import data.web.BookFileType
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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.io.InputStream
import java.lang.RuntimeException
import java.time.ZoneId

@Resource("/wenku")
private class WenkuNovel {
    @Resource("/list")
    data class List(
        val parent: WenkuNovel,
        val page: Int,
        val query: String? = null,
    )

    // 如果用的人多，看情况合并到Book里面
    @Resource("/non-archived")
    class NonArchived(val parent: WenkuNovel) {
        @Resource("/prepare-book/{fileName}/{lang}")
        class PrepareBook(
            val parent: NonArchived,
            val fileName: String,
            val lang: BookFileLang,
        )

        @Resource("/{fileName}")
        class EpubInfo(val parent: NonArchived, val fileName: String) {
            @Resource("/{chapterId}")
            class Chapter(val parent: EpubInfo, val chapterId: String) {
                @Resource("/baidu")
                class Baidu(val parent: Chapter)

                @Resource("/youdao")
                class Youdao(val parent: Chapter)
            }
        }
    }

    @Resource("/{bookId}")
    class Book(val parent: WenkuNovel, val bookId: String) {
        @Resource("/episode")
        class Episode(val parent: Book)
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

    get<WenkuNovel.NonArchived> {
        val result = service.listNonArchived()
        call.respondResult(result)
    }

    post<WenkuNovel.NonArchived> {
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem) {
                val fileName = part.originalFileName!!
                val inputStream = part.streamProvider()
                val result = service.createNonArchived(fileName, inputStream)
                call.respondResult(result)
                return@forEachPart
            }
            part.dispose()
        }
    }

    get<WenkuNovel.NonArchived.PrepareBook> { loc ->
        val result = service.updateBookFile(loc.fileName, loc.lang)
        result.onSuccess {
            call.respondRedirect(it)
        }.onFailure {
            call.respondResult(result)
        }
    }

    get<WenkuNovel.NonArchived.EpubInfo> { loc ->
        val result = service.getEpubInfo(loc.fileName)
        call.respondResult(result)
    }

    get<WenkuNovel.NonArchived.EpubInfo.Chapter> { loc ->
        val result = service.getEpubChapter(loc.parent.fileName, loc.chapterId)
        call.respondResult(result)
    }

    post<WenkuNovel.NonArchived.EpubInfo.Chapter.Baidu> { loc ->
        val body = call.receive<List<String>>()
        val result = service.updateEpubChapter(loc.parent.parent.fileName, loc.parent.chapterId, "baidu", body)
        call.respondResult(result)
    }

    post<WenkuNovel.NonArchived.EpubInfo.Chapter.Youdao> { loc ->
        val body = call.receive<List<String>>()
        val result = service.updateEpubChapter(loc.parent.parent.fileName, loc.parent.chapterId, "youdao", body)
        call.respondResult(result)
    }

    get<WenkuNovel.NonArchived.PrepareBook> { loc ->
//        val result = service.prepareBook(loc.fileName, loc.lang, loc.type)
//        result.onSuccess {
//            call.respondRedirect(it)
//        }.onFailure {
//            call.respondResult(result)
//        }
    }

    authenticate(optional = true) {
        get<WenkuNovel.Book> { loc ->
            val user = call.jwtUserOrNull()
            val result = service.getMetadata(loc.bookId, user?.username)
            call.respondResult(result)
        }
    }

    authenticate {
        post<WenkuNovel> {
            val result = call.requireAtLeastMaintainer {
                val body = call.receive<WenkuNovelService.MetadataCreateBody>()
                service.createMetadata(body)
            }
            call.respondResult(result)
        }

        patch<WenkuNovel.Book> { loc ->
            val result = call.requireAtLeastMaintainer {
                val body = call.receive<WenkuNovelService.MetadataCreateBody>()
                service.patchMetadata(loc.bookId, body)
            }
            call.respondResult(result)
        }

        post<WenkuNovel.Book.Episode> { loc ->
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
    private val userRepo: UserRepository,
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
        val favored: Boolean?,
    )

    suspend fun getMetadata(
        bookId: String,
        username: String?,
    ): Result<MetadataDto> {
        val user = username?.let { userRepo.getByUsername(it) }
        val metadata = metadataRepo.findOneAndIncreaseVisited(bookId)
            ?: return httpNotFound("书不存在")
        val files = fileRepo.list(bookId)
        val metadataDto = MetadataDto(
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
            favored = user?.favoriteWenkuBooks?.contains(bookId),
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

    @Serializable
    data class VolumeStateDto(val fileName: String, val jp: Int, val baidu: Int, val youdao: Int)

    suspend fun listNonArchived(): Result<List<VolumeStateDto>> {
        val novelId = "non-archived"
        val states = fileRepo.list(novelId).map { fileName ->
            val jpItems = fileRepo.listUnpackItems(novelId, fileName, "jp")
            val baiduItems = fileRepo.listUnpackItems(novelId, fileName, "baidu")
            val youdaoItems = fileRepo.listUnpackItems(novelId, fileName, "youdao")
            VolumeStateDto(fileName, jp = jpItems.size, baidu = baiduItems.size, youdao = youdaoItems.size)
        }
        return Result.success(states)
    }

    suspend fun createNonArchived(fileName: String, inputStream: InputStream) =
        createEpisodeFile("non-archived", fileName, inputStream)
            .onSuccess {
                runCatching {
                    fileRepo.unpackEpub("non-archived", fileName)
                }.onFailure {
                    fileRepo.delete("non-archived", fileName)
                }
            }

    @Serializable
    data class ChapterStateDto(val chapterId: String, val baidu: Boolean, val youdao: Boolean)

    suspend fun getEpubInfo(fileName: String): Result<List<ChapterStateDto>> {
        val novelId = "non-archived"
        val jpItems = fileRepo.listUnpackItems(novelId, fileName, "jp")
        val baiduItems = fileRepo.listUnpackItems(novelId, fileName, "baidu")
        val youdaoItems = fileRepo.listUnpackItems(novelId, fileName, "youdao")
        val states = jpItems.map {
            ChapterStateDto(
                chapterId = it,
                baidu = baiduItems.contains(it),
                youdao = youdaoItems.contains(it),
            )
        }
        return Result.success(states)
    }

    suspend fun getEpubChapter(fileName: String, chapterId: String): Result<List<String>> {
        val novelId = "non-archived"
        val text = fileRepo.getUnpackItem(novelId, fileName, "jp", chapterId)?.lines()
            ?: return httpNotFound("章节不存在")
        return Result.success(text)
    }

    suspend fun updateEpubChapter(
        fileName: String,
        chapterId: String,
        version: String,
        content: List<String>,
    ): Result<Unit> {
        val novelId = "non-archived"
        val text = fileRepo.getUnpackItem(novelId, fileName, "jp", chapterId)?.lines()
            ?: return httpNotFound("章节不存在")
        if (text.size != content.size) {
            return httpBadRequest("翻译行数不匹配")
        }
        fileRepo.createUnpackItem(novelId, fileName, version, chapterId, content)
        return Result.success(Unit)
    }

    suspend fun updateBookFile(
        fileName: String,
        lang: BookFileLang,
    ): Result<String> {
        val novelId = "non-archived"
        val jpItems = fileRepo.listUnpackItems(novelId, fileName, "jp")

        val version = when (lang) {
            BookFileLang.ZH_YOUDAO, BookFileLang.MIX_YOUDAO -> "youdao"
            BookFileLang.ZH_BAIDU, BookFileLang.MIX_BAIDU -> "baidu"
            else -> throw RuntimeException()
        }
        val zhItems =
            if (version == "baidu") fileRepo.listUnpackItems(novelId, fileName, "baidu")
            else fileRepo.listUnpackItems(novelId, fileName, "youdao")
        if (jpItems.size != zhItems.size) {
            return httpBadRequest("还没有翻译完成")
        }
        val newFileName = fileRepo.makeFile(
            novelId = novelId,
            fileName = fileName,
            lang = lang,
        )
        return Result.success("../../../../../../files-wenku/non-archived/$newFileName")
    }
}
