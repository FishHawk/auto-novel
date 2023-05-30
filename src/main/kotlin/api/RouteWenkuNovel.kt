package api

import api.dto.PageDto
import api.dto.VolumeJpDto
import api.dto.WenkuNovelDto
import api.dto.WenkuNovelOutlineDto
import infra.UserRepository
import infra.model.NovelFileLang
import infra.model.TranslatorId
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
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

@Resource("/wenku")
private class WenkuNovelRes {
    @Resource("/list")
    data class List(
        val parent: WenkuNovelRes,
        val page: Int,
        val query: String? = null,
    )

    @Resource("/non-archived")
    class NonArchived(val parent: WenkuNovelRes)

    @Resource("/{novelId}")
    class Id(val parent: WenkuNovelRes, val novelId: String) {
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
    val service by inject<WenkuNovelApi>()

    get<WenkuNovelRes.List> { loc ->
        val result = service.list(
            queryString = loc.query?.ifBlank { null },
            page = loc.page,
            pageSize = 24,
        )
        call.respondResult(result)
    }


    get<WenkuNovelRes.NonArchived> { loc ->
        val result = service.getNonArchived()
        call.respondResult(result)
    }

    authenticate(optional = true) {
        get<WenkuNovelRes.Id> { loc ->
            val user = call.jwtUserOrNull()
            val result = service.getMetadata(loc.novelId, user?.username)
            call.respondResult(result)
        }
    }

    authenticate {
        post<WenkuNovelRes> {
            val result = call.requireAtLeastMaintainer {
                val body = call.receive<WenkuNovelApi.MetadataCreateBody>()
                service.createMetadata(body)
            }
            call.respondResult(result)
        }

        patch<WenkuNovelRes.Id> { loc ->
            val result = call.requireAtLeastMaintainer {
                val body = call.receive<WenkuNovelApi.MetadataCreateBody>()
                service.patchMetadata(loc.novelId, body)
            }
            call.respondResult(result)
        }
    }

    post<WenkuNovelRes.Id.VolumeZh> { loc ->
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

    post<WenkuNovelRes.Id.VolumeJp> { loc ->
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
    get<WenkuNovelRes.Id.Translate> { loc ->
        val result = service.listUntranslatedChapter(
            novelId = loc.parent.novelId,
            translatorId = loc.translatorId,
            volumeId = loc.volumeId,
        )
        call.respondResult(result)
    }

    get<WenkuNovelRes.Id.Translate.Chapter> { loc ->
        val result = service.getChapterToTranslate(
            novelId = loc.parent.parent.novelId,
            volumeId = loc.parent.volumeId,
            chapterId = loc.chapterId,
        )
        call.respondResult(result)
    }

    post<WenkuNovelRes.Id.Translate.Chapter> { loc ->
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
    get<WenkuNovelRes.Id.File> { loc ->
        val result = service.updateFile(
            novelId = loc.parent.novelId,
            volumeId = loc.volumeId,
            lang = loc.lang,
        )
        result.onSuccess {
            val url = "../../../../../../files-wenku/${loc.parent.novelId}/" +
                    "${loc.volumeId}.unpack".encodeURLPathPart() +
                    "/${loc.lang.value}.epub"
            call.respondRedirect(url)
        }.onFailure {
            call.respondResult(result)
        }
    }
}

class WenkuNovelApi(
    private val userRepo: UserRepository,
    private val metadataRepo: WenkuNovelMetadataRepository,
    private val volumeRepo: WenkuNovelVolumeRepository,
) {
    suspend fun list(
        queryString: String?,
        page: Int,
        pageSize: Int,
    ): Result<PageDto<WenkuNovelOutlineDto>> {
        val novelPage = metadataRepo.search(
            queryString = queryString,
            page = page,
            pageSize = pageSize,
        )
        val dto = PageDto.fromPage(novelPage, pageSize) {
            WenkuNovelOutlineDto.fromDomain(it)
        }
        return Result.success(dto)
    }

    suspend fun getNonArchived(): Result<List<VolumeJpDto>> {
        val novelId = "non-archived"
        val volumes = volumeRepo.list(novelId).jp.map { volumeId ->
            val state = volumeRepo.getTranslationState(novelId, volumeId)
            VolumeJpDto.fromDomain(volumeId, state)
        }
        return Result.success(volumes)
    }

    suspend fun getMetadata(
        novelId: String,
        username: String?,
    ): Result<WenkuNovelDto> {
        val favored = username?.let {
            userRepo.isUserFavoriteWenkuNovel(it, novelId)
        }

        val metadata = metadataRepo.findOneAndIncreaseVisited(novelId)
            ?: return httpNotFound("书不存在")

        val volumes = volumeRepo.list(novelId)
        val volumeJp = volumes.jp.map { volumeId ->
            val state = volumeRepo.getTranslationState(novelId, volumeId)
            VolumeJpDto.fromDomain(volumeId, state)
        }

        val metadataDto = WenkuNovelDto(
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
            favored = favored,
            volumeZh = volumes.zh,
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
        return Result.success(Unit)
    }

    suspend fun createVolume(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ): Result<Unit> {
        if (novelId != "non-archived" && !metadataRepo.exist(novelId))
            return httpNotFound("小说不存在")

        val outputStream = volumeRepo.createVolumeAndOpen(novelId, volumeId)
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
            volumeRepo.deleteVolumeIfExist(novelId, volumeId)
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
            volumeRepo.unpackVolume(novelId, volumeId)
        }.onFailure {
            volumeRepo.deleteVolumeJpIfExist(novelId, volumeId)
        }
    }

    // Translate
    suspend fun listUntranslatedChapter(
        novelId: String,
        translatorId: String,
        volumeId: String,
    ): Result<List<String>> {
        val realTranslatorId = when (translatorId) {
            "baidu" -> TranslatorId.Baidu
            "youdao" -> TranslatorId.Youdao
            else -> return httpBadRequest("不支持的版本")
        }

        if (novelId != "non-archived" && !metadataRepo.exist(novelId))
            return httpNotFound("小说不存在")

        if (!volumeRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val untranslatedChapters = volumeRepo.listUntranslatedChapter(
            novelId = novelId,
            volumeId = volumeId,
            translatorId = realTranslatorId,
        )
        return Result.success(untranslatedChapters)
    }

    suspend fun getChapterToTranslate(
        novelId: String,
        volumeId: String,
        chapterId: String,
    ): Result<List<String>> {
        val lines = volumeRepo.getChapter(novelId, volumeId, chapterId)
            ?: return httpNotFound("章节不存在")
        return Result.success(lines)
    }

    suspend fun updateChapterTranslation(
        novelId: String,
        translatorId: String,
        volumeId: String,
        chapterId: String,
        lines: List<String>,
    ): Result<Long> {
        val realTranslatorId = when (translatorId) {
            "baidu" -> TranslatorId.Baidu
            "youdao" -> TranslatorId.Youdao
            else -> return httpBadRequest("不支持的版本")
        }

        val jpLines = volumeRepo.getChapter(novelId, volumeId, chapterId)
            ?: return httpNotFound("章节不存在")

        if (volumeRepo.isTranslationExist(novelId, volumeId, realTranslatorId, chapterId))
            return httpConflict("章节翻译已经存在")

        if (jpLines.size != lines.size)
            return httpBadRequest("翻译行数不匹配")

        volumeRepo.updateTranslation(
            novelId = novelId,
            volumeId = volumeId,
            translatorId = realTranslatorId,
            chapterId = chapterId,
            lines = lines,
        )

        val state = volumeRepo.getTranslationState(novelId, volumeId)
        val zhChapters = when (realTranslatorId) {
            TranslatorId.Baidu -> state.baidu
            TranslatorId.Youdao -> state.youdao
        }
        return Result.success(zhChapters)
    }

    // File
    suspend fun updateFile(
        novelId: String,
        volumeId: String,
        lang: NovelFileLang,
    ): Result<String> {
        if (lang == NovelFileLang.JP)
            return httpBadRequest("不支持的类型")

        if (!volumeRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val newFileName = volumeRepo.makeTranslationVolumeFile(
            novelId = novelId,
            volumeId = volumeId,
            lang = lang,
        )
        return Result.success(newFileName)
    }
}
