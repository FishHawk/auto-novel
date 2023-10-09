package api

import infra.common.StatisticsRepository
import infra.common.UserRepository
import infra.model.*
import infra.wenku.WenkuNovelEditHistoryRepository
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelUploadHistoryRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
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
    class NovelList(
        val parent: WenkuNovelRes,
        val page: Int,
        val pageSize: Int = 24,
        val query: String? = null,
    )

    @Resource("/favored")
    class Favored(
        val parent: WenkuNovelRes,
        val page: Int,
        val pageSize: Int = 24,
        val sort: FavoriteListSort,
    )

    @Resource("/non-archived")
    class VolumesNonArchived(val parent: WenkuNovelRes)

    @Resource("/user")
    class VolumesUser(val parent: WenkuNovelRes)

    @Resource("/{novelId}")
    class Id(val parent: WenkuNovelRes, val novelId: String) {
        @Resource("/favored")
        class Favored(val parent: Id)

        @Resource("/glossary")
        class Glossary(val parent: Id)

        @Resource("/volume-zh")
        class VolumeZh(val parent: Id)

        @Resource("/volume-jp")
        class VolumeJp(val parent: Id)

        @Resource("/translate/{translatorId}/{volumeId}")
        class Translate(val parent: Id, val translatorId: TranslatorId, val volumeId: String) {
            @Resource("/{chapterId}")
            class Chapter(val parent: Translate, val chapterId: String)
        }

        @Resource("/file/{volumeId}")
        class File(
            val parent: Id,
            val volumeId: String,
            val lang: NovelFileLangV2,
            val translationsMode: NovelFileTranslationsMode,
            val translations: List<TranslatorId>,
        )
    }
}

fun Route.routeWenkuNovel() {
    val service by inject<WenkuNovelApi>()

    get<WenkuNovelRes.NovelList> { loc ->
        val result = service.list(
            queryString = loc.query?.ifBlank { null },
            page = loc.page.coerceAtLeast(0),
            pageSize = loc.pageSize.coerceIn(1, 24),
        )
        call.respondResult(result)
    }
    authenticate {
        get<WenkuNovelRes.Favored> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listFavored(
                username = jwtUser.username,
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceIn(1, 24),
                sort = loc.sort,
            )
            call.respondResult(result)
        }
        put<WenkuNovelRes.Id.Favored> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.setFavored(
                username = jwtUser.username,
                novelId = loc.parent.novelId,
            )
            call.respondResult(result)
        }
        delete<WenkuNovelRes.Id.Favored> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavored(
                username = jwtUser.username,
                novelId = loc.parent.novelId,
            )
            call.respondResult(result)
        }
    }

    get<WenkuNovelRes.VolumesNonArchived> { loc ->
        val result = service.getNonArchivedVolumes()
        call.respondResult(result)
    }
    authenticate {
        get<WenkuNovelRes.VolumesUser> { loc ->
            val user = call.jwtUser()
            val result = service.getUserVolumes(user.username)
            call.respondResult(result)
        }
    }

    authenticate(optional = true) {
        get<WenkuNovelRes.Id> { loc ->
            val user = call.jwtUserOrNull()
            val result = service.getNovel(loc.novelId, user?.username)
            call.respondResult(result)
        }
    }

    authenticate {
        post<WenkuNovelRes> {
            val user = call.jwtUser()
            val body = call.receive<WenkuNovelApi.MetadataCreateBody>()
            val result = service.createNovel(body, user.username)
            call.respondResult(result)
        }

        put<WenkuNovelRes.Id> { loc ->
            val user = call.jwtUser()
            val body = call.receive<WenkuNovelApi.MetadataCreateBody>()
            val result = service.updateNovel(loc.novelId, body, user.username)
            call.respondResult(result)
        }

        put<WenkuNovelRes.Id.Glossary> { loc ->
            val body = call.receive<Map<String, String>>()
            val result = service.updateGlossary(loc.parent.novelId, body)
            call.respondResult(result)
        }

        post<WenkuNovelRes.Id.VolumeZh> { loc ->
            val user = call.jwtUser()
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName!!
                    val inputStream = part.streamProvider()
                    val result = service.createNovelVolumeZh(
                        loc.parent.novelId, fileName, inputStream, user.username
                    )
                    call.respondResult(result)
                    return@forEachPart
                }
                part.dispose()
            }
        }

        post<WenkuNovelRes.Id.VolumeJp> { loc ->
            val user = call.jwtUser()
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName!!
                    val inputStream = part.streamProvider()
                    val result = service.createNovelVolumeJp(
                        loc.parent.novelId, fileName, inputStream, user.username
                    )
                    call.respondResult(result)
                    return@forEachPart
                }
                part.dispose()
            }
        }
    }

    // Translate
    get<WenkuNovelRes.Id.Translate> { loc ->
        val result = service.getTranslateTask(
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
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
        )
        call.respondResult(result)
    }
    put<WenkuNovelRes.Id.Translate.Chapter> { loc ->
        @Serializable
        class Body(
            val glossaryUuid: String? = null,
            val paragraphsZh: List<String>,
        )

        val body = call.receive<Body>()
        val result = service.updateChapterTranslation(
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            volumeId = loc.parent.volumeId,
            chapterId = loc.chapterId,
            glossaryUuid = body.glossaryUuid,
            paragraphsZh = body.paragraphsZh,
        )
        call.respondResult(result)
    }

    // File
    get<WenkuNovelRes.Id.File> { loc ->
        val result = service.updateFile(
            novelId = loc.parent.novelId,
            volumeId = loc.volumeId,
            lang = loc.lang,
            translationsMode = loc.translationsMode,
            translations = loc.translations,
        )
        result.onSuccess {
            val url = "../../../../../../files-wenku/$it"
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
    private val uploadHistoryRepo: WenkuNovelUploadHistoryRepository,
    private val editHistoryRepository: WenkuNovelEditHistoryRepository,
    private val statisticsRepo: StatisticsRepository,
) {
    @Serializable
    class NovelOutlineDto(
        val id: String,
        val title: String,
        val titleZh: String,
        val cover: String,
    ) {
        constructor(it: WenkuNovelMetadataOutline) : this(
            id = it.id,
            title = it.title,
            titleZh = it.titleZh,
            cover = it.cover,
        )
    }

    suspend fun list(
        queryString: String?,
        page: Int,
        pageSize: Int,
    ): Result<PageDto<NovelOutlineDto>> {
        val novelPage = metadataRepo.search(
            userQuery = queryString,
            page = page,
            pageSize = pageSize,
        )
        val dto = PageDto.fromPage(novelPage, pageSize) {
            NovelOutlineDto(it)
        }
        return Result.success(dto)
    }

    suspend fun listFavored(
        username: String,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): Result<PageDto<NovelOutlineDto>> {
        val novelPage = userRepo.listFavoriteWenkuNovel(
            username = username,
            page = page,
            pageSize = pageSize,
            sort = sort,
        )
        val dto = PageDto.fromPage(novelPage, pageSize) {
            NovelOutlineDto(it)
        }
        return Result.success(dto)
    }

    suspend fun getNonArchivedVolumes(): Result<List<WenkuNovelVolumeJp>> {
        val volumes = volumeRepo.listVolumesNonArchived()
        return Result.success(volumes)
    }

    @Serializable
    class WenkuUserVolumeDto(
        val list: List<WenkuNovelVolumeJp>,
        val novelId: String,
    )

    suspend fun getUserVolumes(username: String): Result<WenkuUserVolumeDto> {
        val userId = userRepo.getUserIdByUsername(username).toHexString()
        val novelId = "user-${userId}"
        val volumes = volumeRepo.listVolumesUser(novelId)
        return Result.success(WenkuUserVolumeDto(list = volumes, novelId = novelId))
    }

    @Serializable
    class NovelDto(
        val title: String,
        val titleZh: String,
        val cover: String,
        val coverSmall: String,
        val authors: List<String>,
        val artists: List<String>,
        val keywords: List<String>,
        val introduction: String,
        val glossary: Map<String, String>,
        val visited: Long,
        val favored: Boolean?,
        val volumeZh: List<String>,
        val volumeJp: List<WenkuNovelVolumeJp>,
    )

    suspend fun getNovel(
        novelId: String,
        username: String?,
    ): Result<NovelDto> {
        val favored = username?.let {
            userRepo.isUserFavoriteWenkuNovel(it, novelId)
        }

        val metadata = metadataRepo.get(novelId)
            ?: return httpNotFound("书不存在")
        if (username != null) {
            statisticsRepo.increaseWenkuNovelVisited(
                usernameOrIp = username,
                novelId = novelId,
            )
        }

        val volumes = volumeRepo.list(novelId)

        val metadataDto = NovelDto(
            title = metadata.title,
            titleZh = metadata.titleZh,
            cover = metadata.cover,
            coverSmall = metadata.coverSmall,
            authors = metadata.authors,
            artists = metadata.artists,
            keywords = metadata.keywords,
            introduction = metadata.introduction,
            glossary = metadata.glossary,
            visited = metadata.visited,
            favored = favored,
            volumeZh = volumes.zh,
            volumeJp = volumes.jp,
        )
        return Result.success(metadataDto)
    }

    suspend fun setFavored(username: String, novelId: String): Result<Unit> {
        if (!metadataRepo.exist(novelId)) {
            return httpNotFound("书不存在")
        }
        userRepo.addFavoriteWenkuNovel(
            username = username,
            novelId = novelId,
        )
        return Result.success(Unit)
    }

    suspend fun removeFavored(username: String, novelId: String): Result<Unit> {
        if (!metadataRepo.exist(novelId)) {
            return httpNotFound("书不存在")
        }
        userRepo.removeFavoriteWenkuNovel(
            username = username,
            novelId = novelId,
        )
        return Result.success(Unit)
    }

    @Serializable
    class MetadataCreateBody(
        val title: String,
        val titleZh: String,
        val cover: String,
        val coverSmall: String,
        val authors: List<String>,
        val artists: List<String>,
        val keywords: List<String>,
        val introduction: String,
    )

    suspend fun createNovel(
        body: MetadataCreateBody,
        username: String,
    ): Result<String> {
        val novelId = metadataRepo.create(
            title = body.title,
            titleZh = body.titleZh,
            cover = body.cover,
            coverSmall = body.coverSmall,
            authors = body.authors,
            artists = body.artists,
            keywords = body.keywords,
            introduction = body.introduction,
        )
        editHistoryRepository.create(
            novelId,
            operator = username,
            old = null,
            new = WenkuNovelEditHistory.Data(
                title = body.title,
                titleZh = body.titleZh,
                authors = body.authors,
                artists = body.artists,
                introduction = body.introduction,
            ),
        )
        return Result.success(novelId)
    }

    suspend fun updateNovel(
        novelId: String,
        body: MetadataCreateBody,
        username: String,
    ): Result<Unit> {
        val old = metadataRepo.get(novelId)
            ?: return httpNotFound("书不存在")
        metadataRepo.update(
            novelId = novelId,
            title = body.title,
            titleZh = body.titleZh,
            cover = body.cover,
            coverSmall = body.coverSmall,
            authors = body.authors,
            artists = body.artists,
            keywords = body.keywords,
            introduction = body.introduction,
        )
        editHistoryRepository.create(
            novelId,
            operator = username,
            old = WenkuNovelEditHistory.Data(
                title = old.title,
                titleZh = old.titleZh,
                authors = old.authors,
                artists = old.artists,
                introduction = old.introduction,
            ),
            new = WenkuNovelEditHistory.Data(
                title = body.title,
                titleZh = body.titleZh,
                authors = body.authors,
                artists = body.artists,
                introduction = body.introduction,
            ),
        )
        return Result.success(Unit)
    }

    suspend fun updateGlossary(
        novelId: String,
        glossary: Map<String, String>,
    ): Result<Unit> {
        val metadata = metadataRepo.get(novelId)
            ?: return httpNotFound("小说不存在")
        if (glossary == metadata.glossary)
            return httpBadRequest("术语表没有改变")
        metadataRepo.updateGlossary(novelId, glossary)
        return Result.success(Unit)
    }

    private suspend fun createVolume(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ): Result<Unit> {
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

    private suspend fun createVolumeAndUnpack(
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

    suspend fun createNovelVolumeZh(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
        username: String,
    ): Result<Unit> {
        return if (!metadataRepo.exist(novelId)) {
            httpNotFound("小说不存在")
        } else {
            createVolume(novelId, volumeId, inputStream).onSuccess {
                uploadHistoryRepo.create(
                    novelId = novelId,
                    volumeId = volumeId,
                    uploader = username,
                )
                metadataRepo.notifyUpdate(novelId)
            }
        }
    }

    private suspend fun validateNovelId(novelId: String): Boolean {
        return if (novelId == "non-archived" || novelId.startsWith("user")) true
        else metadataRepo.exist(novelId)
    }

    suspend fun createNovelVolumeJp(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
        username: String,
    ): Result<Unit> {
        if (!validateNovelId(novelId)) return httpNotFound("小说不存在")
        return createVolumeAndUnpack(novelId, volumeId, inputStream).onSuccess {
            if (!(novelId == "non-archived" || novelId.startsWith("user"))) {
                uploadHistoryRepo.create(
                    novelId = novelId,
                    volumeId = volumeId,
                    uploader = username,
                )
                metadataRepo.notifyUpdate(novelId)
            }
        }
    }

    // Translate
    @Serializable
    class TranslateTaskDto(
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val untranslatedChapters: List<String>,
        val expiredChapters: List<String>,
    )

    suspend fun getTranslateTask(
        novelId: String,
        translatorId: TranslatorId,
        volumeId: String,
    ): Result<TranslateTaskDto> {
        if (!validateNovelId(novelId)) return httpNotFound("小说不存在")

        val novel =
            if (novelId == "non-archived" || novelId.startsWith("user")) null
            else metadataRepo.get(novelId)

        if (!volumeRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val untranslatedChapterIds = mutableListOf<String>()
        val expiredChapterIds = mutableListOf<String>()

        volumeRepo.listUnpackedChapters(novelId, volumeId, "jp").forEach {
            if (!volumeRepo.isTranslationExist(novelId, volumeId, translatorId, it)) {
                untranslatedChapterIds.add(it)
            } else if (
                volumeRepo.getChapterGlossary(novelId, volumeId, translatorId, it)?.uuid != novel?.glossaryUuid
            ) {
                expiredChapterIds.add(it)
            }
        }
        return Result.success(
            TranslateTaskDto(
                glossaryUuid = novel?.glossaryUuid,
                glossary = novel?.glossary ?: emptyMap(),
                untranslatedChapters = untranslatedChapterIds,
                expiredChapters = expiredChapterIds,
            )
        )
    }

    suspend fun getChapterToTranslate(
        novelId: String,
        volumeId: String,
        translatorId: TranslatorId,
        chapterId: String,
    ): Result<List<String>> {
        val lines = volumeRepo.getChapter(novelId, volumeId, chapterId)
            ?: return httpNotFound("章节不存在")
        return Result.success(lines)
    }

    suspend fun updateChapterTranslation(
        novelId: String,
        translatorId: TranslatorId,
        volumeId: String,
        chapterId: String,
        glossaryUuid: String?,
        paragraphsZh: List<String>,
    ): Result<Long> {
        if (!validateNovelId(novelId))
            return httpNotFound("小说不存在")

        val novel =
            if (novelId == "non-archived" || novelId.startsWith("user")) null
            else metadataRepo.get(novelId)

        if (glossaryUuid != novel?.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val jpLines = volumeRepo.getChapter(novelId, volumeId, chapterId)
            ?: return httpNotFound("章节不存在")

        if (jpLines.size != paragraphsZh.size)
            return httpBadRequest("翻译行数不匹配")

        volumeRepo.updateTranslation(
            novelId = novelId,
            volumeId = volumeId,
            translatorId = translatorId,
            chapterId = chapterId,
            lines = paragraphsZh,
            glossaryUuid = glossaryUuid,
            glossary = novel?.glossary ?: emptyMap(),
        )

        val zhChapters = volumeRepo.getTranslatedNumber(
            novelId, volumeId, translatorId
        )
        return Result.success(zhChapters)
    }

    // File
    suspend fun updateFile(
        novelId: String,
        volumeId: String,
        lang: NovelFileLangV2,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
    ): Result<String> {
        if (translations.isEmpty())
            return httpBadRequest("没有设置翻译类型")

        if (lang == NovelFileLangV2.Jp)
            return httpBadRequest("不支持的类型")

        if (!volumeRepo.isVolumeJpExisted(novelId, volumeId))
            return httpNotFound("卷不存在")

        val newFileName = volumeRepo.makeTranslationVolumeFile(
            novelId = novelId,
            volumeId = volumeId,
            lang = lang,
            translationsMode = translationsMode,
            translations = translations.distinct(),
        )

        return Result.success(
            "${novelId}/${volumeId.encodeURLPathPart()}.unpack/$newFileName"
        )
    }
}
