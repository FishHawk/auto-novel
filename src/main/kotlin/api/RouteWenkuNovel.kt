package api

import data.UserRepository
import data.web.NovelFileLang
import data.wenku.WenkuNovelFileRepository
import data.wenku.WenkuNovelIndexRepository
import data.wenku.WenkuNovelMetadataRepository
import io.ktor.http.*
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
import kotlin.io.path.div

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

        @Resource("/translate/{translatorId}/{volumeId}")
        class Translate(val parent: Id, val translatorId: String, val volumeId: String) {
            @Resource("/{chapterId}")
            class Chapter(val parent: Translate, val chapterId: String)
        }

        @Resource("/file/{volumeId}/{lang}")
        data class File(val parent: Id, val volumeId: String, val lang: NovelFileLang)
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

    // Translate
    get<WenkuNovel.Id.Translate> { loc ->
        val result = service.getUntranslatedChapter(
            novelId = loc.parent.novelId,
            translatorId = loc.translatorId,
            volumeId = loc.volumeId,
        )
        call.respondResult(result)
    }

    get<WenkuNovel.Id.Translate.Chapter> { loc ->
        val result = service.getChapterToTranslate(
            novelId = loc.parent.parent.novelId,
            volumeId = loc.parent.volumeId,
            chapterId = loc.chapterId,
        )
        call.respondResult(result)
    }

    post<WenkuNovel.Id.Translate.Chapter> { loc ->
        val body = call.receive<List<String>>()
        val result = service.updateChapterTranslation(
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            volumeId = loc.parent.volumeId,
            chapterId = loc.chapterId,
            lines = body,
        )
        call.respondResult(result)
    }

    // File
    get<WenkuNovel.Id.File> { loc ->
        val result = service.updateFile(
            novelId = loc.parent.novelId,
            volumeId = loc.volumeId,
            lang = loc.lang,
        )
        result.onSuccess {
            call.respondRedirect {
                path(
                    "..",
                    "files-wenku",
                    loc.parent.novelId,
                    "${loc.volumeId}.unpack",
                    "${loc.lang.value}.epub",
                )
            }
        }.onFailure {
            call.respondResult(result)
        }
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
                jp = fileRepo.listUnpackedChapters(novelId, volumeId, "jp").size,
                baidu = fileRepo.listUnpackedChapters(novelId, volumeId, "baidu").size,
                youdao = fileRepo.listUnpackedChapters(novelId, volumeId, "youdao").size,
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
                jp = fileRepo.listUnpackedChapters(novelId, volumeId, "jp").size,
                baidu = fileRepo.listUnpackedChapters(novelId, volumeId, "baidu").size,
                youdao = fileRepo.listUnpackedChapters(novelId, volumeId, "youdao").size,
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
        if (novelId != "non-archived" && !metadataRepo.exist(novelId))
            return httpNotFound("小说不存在")

        val outputStream = fileRepo.createVolumeAndOpen(novelId, volumeId)
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
            fileRepo.deleteVolumeIfExist(novelId, volumeId)
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
            fileRepo.unpackVolume(novelId, volumeId)
        }.onFailure {
            fileRepo.deleteVolumeIfExist(novelId, volumeId)
        }
    }

    // Translate
    suspend fun getUntranslatedChapter(
        novelId: String,
        translatorId: String,
        volumeId: String,
    ): Result<List<String>> {
        if (translatorId != "baidu" && translatorId != "youdao")
            return httpBadRequest("翻译器不支持")

        if (novelId != "non-archived" && !metadataRepo.exist(novelId))
            return httpNotFound("小说不存在")

        if (!fileRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val jpChapters = fileRepo.listUnpackedChapters(novelId, volumeId, "jp")
        val zhChapters =
            when (translatorId) {
                "baidu" -> fileRepo.listUnpackedChapters(novelId, volumeId, "baidu")
                "youdao" -> fileRepo.listUnpackedChapters(novelId, volumeId, "youdao")
                else -> throw RuntimeException("不应当执行到此")
            }
        val untranslatedChapters = jpChapters.filter { it !in zhChapters }
        return Result.success(untranslatedChapters)
    }

    suspend fun getChapterToTranslate(
        novelId: String,
        volumeId: String,
        chapterId: String,
    ): Result<List<String>> {
        if (novelId != "non-archived" && !metadataRepo.exist(novelId))
            return httpNotFound("小说不存在")

        if (!fileRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val lines = fileRepo.getUnpackedChapter(novelId, volumeId, "jp", chapterId)
            ?: return httpNotFound("章节不存在")
        return Result.success(lines)
    }

    suspend fun updateChapterTranslation(
        novelId: String,
        translatorId: String,
        volumeId: String,
        chapterId: String,
        lines: List<String>,
    ): Result<Int> {
        if (translatorId != "baidu" && translatorId != "youdao")
            return httpBadRequest("翻译器不支持")

        if (novelId != "non-archived" && !metadataRepo.exist(novelId))
            return httpNotFound("小说不存在")

        if (!fileRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val jpLines = fileRepo.getUnpackedChapter(novelId, volumeId, "jp", chapterId)
            ?: return httpNotFound("章节不存在")

        if (fileRepo.isUnpackChapterExist(novelId, volumeId, translatorId, chapterId))
            return httpConflict("章节翻译已经存在")

        if (jpLines.size != lines.size)
            return httpBadRequest("翻译行数不匹配")

        fileRepo.createUnpackedChapter(
            novelId = novelId,
            volumeId = volumeId,
            type = translatorId,
            chapterId = chapterId,
            lines = lines,
        )

        val zhChapters = when (translatorId) {
            "baidu" -> fileRepo.listUnpackedChapters(novelId, volumeId, "baidu")
            "youdao" -> fileRepo.listUnpackedChapters(novelId, volumeId, "youdao")
            else -> throw RuntimeException("不应当执行到此")
        }
        return Result.success(zhChapters.size)
    }

    // File
    suspend fun updateFile(
        novelId: String,
        volumeId: String,
        lang: NovelFileLang,
    ): Result<String> {
        if (lang == NovelFileLang.JP)
            return httpBadRequest("不支持的类型")

        if (!fileRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val newFileName = fileRepo.makeFile(
            novelId = novelId,
            volumeId = volumeId,
            lang = lang,
        )
        return Result.success(newFileName)
    }
}
