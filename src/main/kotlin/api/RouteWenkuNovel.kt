package api

import data.UserRepository
import data.web.NovelFileLang
import data.wenku.WenkuNovelFileRepository
import data.wenku.WenkuNovelIndexRepository
import data.wenku.WenkuNovelMetadataRepository
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

@Resource("/wenku")
private class WenkuNovel {
    @Resource("/list")
    data class List(
        val parent: WenkuNovel,
        val page: Int,
        val query: String? = null,
    )

    @Resource("/non-archived")
    class NonArchived(val parent: WenkuNovel)

    @Resource("/{novelId}")
    class Id(val parent: WenkuNovel, val novelId: String) {
        @Resource("/volume-zh")
        class VolumeZh(val parent: Id)

        @Resource("/volume-jp")
        class VolumeJp(val parent: Id)

        @Resource("/volume-generated/{volumeId}/{lang}")
        class VolumeGenerated(val parent: Id, val volumeId: String, val lang: NovelFileLang)

        @Resource("/translate/{volumeId}")
        class Translate(val parent: Id, val volumeId: String) {
            @Resource("/{chapterId}")
            class Chapter(val parent: Translate, val chapterId: String) {
                @Resource("/baidu")
                class Baidu(val parent: Chapter)

                @Resource("/youdao")
                class Youdao(val parent: Chapter)
            }
        }
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


    get<WenkuNovel.NonArchived> { loc ->
        val result = service.getNonArchived()
        call.respondResult(result)
    }

    authenticate(optional = true) {
        get<WenkuNovel.Id> { loc ->
            val user = call.jwtUserOrNull()
            val result = service.getMetadata(loc.novelId, user?.username)
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

        patch<WenkuNovel.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                val body = call.receive<WenkuNovelService.MetadataCreateBody>()
                service.patchMetadata(loc.novelId, body)
            }
            call.respondResult(result)
        }
    }

    post<WenkuNovel.Id.VolumeZh> { loc ->
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem) {
                val fileName = part.originalFileName!!
                val inputStream = part.streamProvider()
                val result = service.createVolume(
                    loc.parent.novelId, fileName, inputStream
                )
                call.respondResult(result)
                return@forEachPart
            }
            part.dispose()
        }
    }

    post<WenkuNovel.Id.VolumeJp> { loc ->
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem) {
                val fileName = part.originalFileName!!
                val inputStream = part.streamProvider()
                val result = service.createVolumeAndUnpack(
                    loc.parent.novelId, fileName, inputStream
                )
                call.respondResult(result)
                return@forEachPart
            }
            part.dispose()
        }
    }

    get<WenkuNovel.Id.VolumeGenerated> { loc ->
        val result = service.updateBookFile(loc.parent.novelId, loc.volumeId, loc.lang)
        result.onSuccess { filePath ->
            call.respondRedirect("../../../../../../files-wenku/${loc.parent.novelId}/${filePath}")
        }.onFailure {
            call.respondResult(result)
        }
    }

    get<WenkuNovel.Id.Translate> { loc ->
        val result = service.getEpubInfo(loc.volumeId)
        call.respondResult(result)
    }

    get<WenkuNovel.Id.Translate.Chapter> { loc ->
        val result = service.getEpubChapter(loc.parent.volumeId, loc.chapterId)
        call.respondResult(result)
    }

    post<WenkuNovel.Id.Translate.Chapter.Baidu> { loc ->
        val body = call.receive<List<String>>()
        val result = service.updateEpubChapter(loc.parent.parent.volumeId, loc.parent.chapterId, "baidu", body)
        call.respondResult(result)
    }

    post<WenkuNovel.Id.Translate.Chapter.Youdao> { loc ->
        val body = call.receive<List<String>>()
        val result = service.updateEpubChapter(loc.parent.parent.volumeId, loc.parent.chapterId, "youdao", body)
        call.respondResult(result)
    }
}

class WenkuNovelService(
    private val userRepo: UserRepository,
    private val fileRepo: WenkuNovelFileRepository,
    private val indexRepo: WenkuNovelIndexRepository,
    private val metadataRepo: WenkuNovelMetadataRepository,
) {
    @Serializable
    data class NovelListPageDto(
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
    ): Result<NovelListPageDto> {
        val esPage = indexRepo.search(
            queryString = queryString,
            page = page,
            pageSize = pageSize,
        )
        val items = esPage.items.map {
            NovelListPageDto.ItemDto(
                id = it.id,
                title = it.title,
                titleZh = it.titleZh,
                cover = it.cover,
            )
        }
        val dto = NovelListPageDto(
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
        val favored: Boolean?,
        val volumeZh: List<String>,
        val volumeJp: List<VolumeJpDto>,
    )

    @Serializable
    data class VolumeJpDto(
        val volumeId: String,
        val jp: Int,
        val baidu: Int,
        val youdao: Int,
    )

    suspend fun getNonArchived(): Result<List<VolumeJpDto>> {
        val novelId = "non-archived"
        val volumes = fileRepo.listVolumeJp(novelId).map { volumeId ->
            VolumeJpDto(
                volumeId = volumeId,
                jp = fileRepo.listUnpackItems(novelId, volumeId, "jp").size,
                baidu = fileRepo.listUnpackItems(novelId, volumeId, "baidu").size,
                youdao = fileRepo.listUnpackItems(novelId, volumeId, "youdao").size,
            )
        }
        return Result.success(volumes)
    }

    suspend fun getMetadata(
        novelId: String,
        username: String?,
    ): Result<MetadataDto> {
        val user = username?.let { userRepo.getByUsername(it) }
        val metadata = metadataRepo.findOneAndIncreaseVisited(novelId)
            ?: return httpNotFound("书不存在")

        val volumeZh = fileRepo.listVolumeZh(novelId)
        val volumeJp = fileRepo.listVolumeJp(novelId).map { volumeId ->
            VolumeJpDto(
                volumeId = volumeId,
                jp = fileRepo.listUnpackItems(novelId, volumeId, "jp").size,
                baidu = fileRepo.listUnpackItems(novelId, volumeId, "baidu").size,
                youdao = fileRepo.listUnpackItems(novelId, volumeId, "youdao").size,
            )
        }

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
            favored = user?.favoriteWenkuBooks?.contains(novelId),
            volumeZh = volumeZh,
            volumeJp = volumeJp,
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
        val novelId = metadataRepo.insertOne(
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
            novelId = novelId,
            title = body.title,
            titleZh = body.titleZh,
            titleZhAlias = body.titleZhAlias,
            cover = body.coverSmall,
            authors = body.authors,
            artists = body.artists,
            keywords = body.keywords,
        )
        return Result.success(novelId)
    }

    suspend fun patchMetadata(
        novelId: String,
        body: MetadataCreateBody,
    ): Result<Unit> {
        metadataRepo.findOne(novelId)
            ?: return httpNotFound("书不存在")
        metadataRepo.findOneAndUpdate(
            novelId = novelId,
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
            novelId = novelId,
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

    suspend fun createVolume(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ): Result<Unit> {
        metadataRepo.findOne(novelId)
            ?: return httpNotFound("书不存在")

        val outputStream = fileRepo.createAndOpen(novelId, volumeId)
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
            fileRepo.delete(novelId, volumeId)
            httpBadRequest("文件大小不能超过40MB")
        } else {
            Result.success(Unit)
        }
    }

    suspend fun createVolumeAndUnpack(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ): Result<Unit> {
        val result = createVolume(novelId, volumeId, inputStream)
        if (result.isFailure) return result
        return runCatching {
            fileRepo.unpackEpub(novelId, volumeId)
        }.onFailure {
            fileRepo.delete(novelId, volumeId)
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
        novelId: String,
        volumeId: String,
        lang: NovelFileLang,
    ): Result<String> {
        val jpItems = fileRepo.listUnpackItems(novelId, volumeId, "jp")

        val version = when (lang) {
            NovelFileLang.ZH_YOUDAO, NovelFileLang.MIX_YOUDAO -> "youdao"
            NovelFileLang.ZH_BAIDU, NovelFileLang.MIX_BAIDU -> "baidu"
            else -> throw RuntimeException()
        }
        val zhItems =
            if (version == "baidu") fileRepo.listUnpackItems(novelId, volumeId, "baidu")
            else fileRepo.listUnpackItems(novelId, volumeId, "youdao")
        if (jpItems.size != zhItems.size) {
            return httpBadRequest("还没有翻译完成")
        }
        val newFileName = fileRepo.makeFile(
            novelId = novelId,
            fileName = volumeId,
            lang = lang,
        )
        return Result.success(newFileName)
    }
}
