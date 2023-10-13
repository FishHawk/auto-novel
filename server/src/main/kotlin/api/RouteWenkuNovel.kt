package api

import infra.common.OperationHistoryRepository
import infra.common.StatisticsRepository
import infra.common.UserRepository
import infra.model.*
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
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject
import java.io.InputStream

@Resource("/wenku")
private class WenkuNovelRes {
    @Resource("")
    class NovelList(
        val parent: WenkuNovelRes,
        val page: Int,
        val pageSize: Int,
        val query: String? = null,
    )

    @Resource("/favored")
    class Favored(
        val parent: WenkuNovelRes,
        val page: Int,
        val pageSize: Int,
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
        call.tryRespond {
            service.list(
                queryString = loc.query?.ifBlank { null },
                page = loc.page,
                pageSize = loc.pageSize,
            )
        }
    }
    authenticate {
        get<WenkuNovelRes.Favored> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listFavored(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    sort = loc.sort,
                )
            }
        }
        put<WenkuNovelRes.Id.Favored> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavored(user = user, novelId = loc.parent.novelId, favored = true)
            }
        }
        delete<WenkuNovelRes.Id.Favored> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavored(user = user, novelId = loc.parent.novelId, favored = false)
            }
        }
    }

    get<WenkuNovelRes.VolumesNonArchived> { loc ->
        call.tryRespond {
            service.getNonArchivedVolumes()
        }
    }
    authenticate {
        get<WenkuNovelRes.VolumesUser> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.getUserVolumes(user = user)
            }
        }
    }

    authenticate(optional = true) {
        get<WenkuNovelRes.Id> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.getNovel(user = user, novelId = loc.novelId)
            }
        }
    }

    authenticate {
        post<WenkuNovelRes> {
            val user = call.authenticatedUser()
            val body = call.receive<WenkuNovelApi.MetadataCreateBody>()
            call.tryRespond {
                service.createNovel(user = user, body = body)
            }
        }
        put<WenkuNovelRes.Id> { loc ->
            val user = call.authenticatedUser()
            val body = call.receive<WenkuNovelApi.MetadataCreateBody>()
            call.tryRespond {
                service.updateNovel(user = user, novelId = loc.novelId, body = body)
            }
        }

        put<WenkuNovelRes.Id.Glossary> { loc ->
            val user = call.authenticatedUser()
            val body = call.receive<Map<String, String>>()
            call.tryRespond {
                service.updateGlossary(user = user, novelId = loc.parent.novelId, glossary = body)
            }
        }

        post<WenkuNovelRes.Id.VolumeZh> { loc ->
            val user = call.authenticatedUser()
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName!!
                    val inputStream = part.streamProvider()
                    call.tryRespond {
                        service.createNovelVolumeZh(
                            user = user,
                            novelId = loc.parent.novelId,
                            volumeId = fileName,
                            inputStream = inputStream,
                        )
                    }
                    return@forEachPart
                }
                part.dispose()
            }
        }

        post<WenkuNovelRes.Id.VolumeJp> { loc ->
            val user = call.authenticatedUser()
            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName!!
                    val inputStream = part.streamProvider()
                    call.tryRespond {
                        service.createNovelVolumeJp(
                            user = user,
                            novelId = loc.parent.novelId,
                            volumeId = fileName,
                            inputStream = inputStream,
                        )
                    }
                    return@forEachPart
                }
                part.dispose()
            }
        }
    }

    // Translate
    get<WenkuNovelRes.Id.Translate> { loc ->
        call.tryRespond {
            service.getTranslateTask(
                novelId = loc.parent.novelId,
                translatorId = loc.translatorId,
                volumeId = loc.volumeId,
            )
        }
    }
    get<WenkuNovelRes.Id.Translate.Chapter> { loc ->
        call.tryRespond {
            service.getChapterToTranslate(
                novelId = loc.parent.parent.novelId,
                volumeId = loc.parent.volumeId,
                translatorId = loc.parent.translatorId,
                chapterId = loc.chapterId,
            )
        }
    }
    put<WenkuNovelRes.Id.Translate.Chapter> { loc ->
        @Serializable
        class Body(
            val glossaryUuid: String? = null,
            val paragraphsZh: List<String>,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.updateChapterTranslation(
                novelId = loc.parent.parent.novelId,
                translatorId = loc.parent.translatorId,
                volumeId = loc.parent.volumeId,
                chapterId = loc.chapterId,
                glossaryUuid = body.glossaryUuid,
                paragraphsZh = body.paragraphsZh,
            )
        }
    }

    // File
    get<WenkuNovelRes.Id.File> { loc ->
        call.tryRespondRedirect {
            val path = service.updateFile(
                novelId = loc.parent.novelId,
                volumeId = loc.volumeId,
                lang = loc.lang,
                translationsMode = loc.translationsMode,
                translations = loc.translations,
            )
            "../../../../../../files-wenku/$path"
        }
    }
}

class WenkuNovelApi(
    private val userRepo: UserRepository,
    private val metadataRepo: WenkuNovelMetadataRepository,
    private val volumeRepo: WenkuNovelVolumeRepository,
    private val operationHistoryRepo: OperationHistoryRepository,
    private val statisticsRepo: StatisticsRepository,
) {
    @Serializable
    data class NovelOutlineDto(
        val id: String,
        val title: String,
        val titleZh: String,
        val cover: String,
    )

    private fun WenkuNovelMetadataOutline.asDto() =
        NovelOutlineDto(
            id = id,
            title = title,
            titleZh = titleZh,
            cover = cover,
        )

    suspend fun list(
        queryString: String?,
        page: Int,
        pageSize: Int,
    ): PageDto<NovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return metadataRepo
            .search(
                userQuery = queryString,
                page = page,
                pageSize = pageSize,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun listFavored(
        user: AuthenticatedUser,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): PageDto<NovelOutlineDto> {
        val user = user.compatEmptyUserId(userRepo)

        validatePageNumber(page)
        validatePageSize(pageSize)
        return userRepo
            .listFavoriteWenkuNovel(
                userId = user.id,
                page = page,
                pageSize = pageSize,
                sort = sort,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun getNonArchivedVolumes(): List<WenkuNovelVolumeJp> {
        return volumeRepo.listVolumesNonArchived()
    }

    @Serializable
    data class WenkuUserVolumeDto(
        val list: List<WenkuNovelVolumeJp>,
        val novelId: String,
    )

    suspend fun getUserVolumes(user: AuthenticatedUser): WenkuUserVolumeDto {
        val user = user.compatEmptyUserId(userRepo)

        val novelId = "user-${user.id}"
        val volumes = volumeRepo.listVolumesUser(novelId)
        return WenkuUserVolumeDto(list = volumes, novelId = novelId)
    }

    private fun throwNovelNotFound(): Nothing =
        throwNotFound("小说不存在")

    @Serializable
    data class NovelDto(
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
        user: AuthenticatedUser?,
        novelId: String,
    ): NovelDto {
        val user = user?.compatEmptyUserId(userRepo)

        val favored = user?.let {
            userRepo.isUserFavoriteWenkuNovel(it.id, novelId)
        }

        val metadata = metadataRepo.get(novelId)
            ?: throwNovelNotFound()
        if (user != null) {
            statisticsRepo.increaseWenkuNovelVisited(
                usernameOrIp = user.username,
                novelId = novelId,
            )
        }

        val volumes = volumeRepo.list(novelId)

        return NovelDto(
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
    }

    suspend fun updateFavored(
        user: AuthenticatedUser,
        novelId: String,
        favored: Boolean,
    ) {
        val user = user.compatEmptyUserId(userRepo)

        if (!metadataRepo.exist(novelId))
            throwNovelNotFound()
        if (favored) {
            userRepo.addFavoriteWenkuNovel(
                userId = user.id,
                novelId = novelId,
            )
        } else {
            userRepo.removeFavoriteWenkuNovel(
                userId = user.id,
                novelId = novelId,
            )
        }
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
        user: AuthenticatedUser,
        body: MetadataCreateBody,
    ): String {
        val user = user.compatEmptyUserId(userRepo)

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
        operationHistoryRepo.createWenkuEditHistory(
            operator = ObjectId(user.id),
            novelId = novelId,
            old = null,
            new = Operation.WenkuEdit.Data(
                title = body.title,
                titleZh = body.titleZh,
                authors = body.authors,
                artists = body.artists,
                introduction = body.introduction,
            ),
        )
        return novelId
    }

    suspend fun updateNovel(
        user: AuthenticatedUser,
        novelId: String,
        body: MetadataCreateBody,
    ) {
        val user = user.compatEmptyUserId(userRepo)

        val old = metadataRepo.get(novelId)
            ?: throwNovelNotFound()
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
        operationHistoryRepo.createWenkuEditHistory(
            operator = ObjectId(user.id),
            novelId = novelId,
            old = Operation.WenkuEdit.Data(
                title = old.title,
                titleZh = old.titleZh,
                authors = old.authors,
                artists = old.artists,
                introduction = old.introduction,
            ),
            new = Operation.WenkuEdit.Data(
                title = body.title,
                titleZh = body.titleZh,
                authors = body.authors,
                artists = body.artists,
                introduction = body.introduction,
            ),
        )
    }

    suspend fun updateGlossary(
        user: AuthenticatedUser,
        novelId: String,
        glossary: Map<String, String>,
    ): Result<Unit> {
        val metadata = metadataRepo.get(novelId)
            ?: throwNovelNotFound()
        if (glossary == metadata.glossary)
            throwBadRequest("术语表没有改变")
        metadataRepo.updateGlossary(novelId, glossary)
        return Result.success(Unit)
    }

    private suspend fun createVolume(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ) {
        val outputStream = volumeRepo.createVolumeAndOpen(novelId, volumeId)
            .getOrElse {
                if (it is FileAlreadyExistsException) {
                    throwConflict("文件已经存在")
                } else {
                    throwInternalServerError(it.message ?: "")
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

        if (fileTooLarge) {
            volumeRepo.deleteVolumeIfExist(novelId, volumeId)
            throwBadRequest("文件大小不能超过40MB")
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun createVolumeAndUnpack(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ) {
        createVolume(novelId, volumeId, inputStream)
        runCatching {
            volumeRepo.unpackVolume(novelId, volumeId)
        }.onFailure {
            volumeRepo.deleteVolumeJpIfExist(novelId, volumeId)
        }
    }

    suspend fun createNovelVolumeZh(
        user: AuthenticatedUser,
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ) {
        val user = user.compatEmptyUserId(userRepo)

        if (!metadataRepo.exist(novelId))
            throwNovelNotFound()
        createVolume(novelId, volumeId, inputStream)
        operationHistoryRepo.createWenkuUploadHistory(
            operator = ObjectId(user.id),
            novelId = novelId,
            volumeId = volumeId,
        )
        metadataRepo.notifyUpdate(novelId)
    }

    private suspend fun validateNovelId(novelId: String): Boolean {
        return if (novelId == "non-archived" || novelId.startsWith("user")) true
        else metadataRepo.exist(novelId)
    }

    suspend fun createNovelVolumeJp(
        user: AuthenticatedUser,
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
    ) {
        val user = user.compatEmptyUserId(userRepo)

        if (!validateNovelId(novelId))
            throwNovelNotFound()

        createVolumeAndUnpack(novelId, volumeId, inputStream)

        if (!(novelId == "non-archived" || novelId.startsWith("user"))) {
            operationHistoryRepo.createWenkuUploadHistory(
                operator = ObjectId(user.id),
                novelId = novelId,
                volumeId = volumeId,
            )
            metadataRepo.notifyUpdate(novelId)
        }
    }

    // Translate
    @Serializable
    data class TranslateTaskDto(
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
        if (!validateNovelId(novelId))
            throwNovelNotFound()

        val novel =
            if (novelId == "non-archived" || novelId.startsWith("user")) null
            else metadataRepo.get(novelId)

        if (!volumeRepo.isVolumeJpExisted(novelId, volumeId))
            throwNotFound("卷不存在")

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
            ?: throwNotFound("章节不存在")
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
            throwNovelNotFound()

        val novel =
            if (novelId == "non-archived" || novelId.startsWith("user")) null
            else metadataRepo.get(novelId)

        if (glossaryUuid != novel?.glossaryUuid) {
            throwBadRequest("术语表uuid失效")
        }

        val jpLines = volumeRepo.getChapter(novelId, volumeId, chapterId)
            ?: throwNotFound("章节不存在")

        if (jpLines.size != paragraphsZh.size)
            throwBadRequest("翻译行数不匹配")

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
    ): String {
        if (translations.isEmpty())
            throwBadRequest("没有设置翻译类型")

        if (lang == NovelFileLangV2.Jp)
            throwBadRequest("不支持的类型")

        if (!volumeRepo.isVolumeJpExisted(novelId, volumeId))
            throwNotFound("卷不存在")

        val newFileName = volumeRepo.makeTranslationVolumeFile(
            novelId = novelId,
            volumeId = volumeId,
            lang = lang,
            translationsMode = translationsMode,
            translations = translations.distinct(),
        )
        return "${novelId}/${volumeId.encodeURLPathPart()}.unpack/$newFileName"
    }
}
