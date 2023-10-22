package api

import api.plugins.*
import infra.common.OperationHistoryRepository
import infra.common.UserRepository
import infra.model.*
import infra.wenku.VolumeCreateException
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
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

        @Resource("/volume/{volumeId}")
        class Volume(val parent: Id, val volumeId: String)

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
    authenticateDb {
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
    authenticateDb {
        get<WenkuNovelRes.VolumesUser> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.getUserVolumes(user = user)
            }
        }
    }

    authenticateDb(optional = true) {
        get<WenkuNovelRes.Id> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.getNovel(user = user, novelId = loc.novelId)
            }
        }
    }

    authenticateDb {
        rateLimit(RateLimitNames.CreateWenkuNovel) {
            post<WenkuNovelRes> {
                val user = call.authenticatedUser()
                val body = call.receive<WenkuNovelApi.MetadataCreateBody>()
                call.tryRespond {
                    service.createNovel(user = user, body = body)
                }
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

        rateLimit(RateLimitNames.CreateWenkuVolume) {
            post<WenkuNovelRes.Id.Volume> { loc ->
                suspend fun MultiPartData.firstFilePart(): PartData.FileItem? {
                    while (true) {
                        val part = readPart() ?: return null
                        if (part is PartData.FileItem) return part
                        else part.dispose()
                    }
                }

                val user = call.authenticatedUser()
                val filePart = call.receiveMultipart().firstFilePart()
                call.tryRespond {
                    if (filePart == null) throwBadRequest("请求里没有文件")
                    service.createVolume(
                        user = user,
                        novelId = loc.parent.novelId,
                        volumeId = loc.volumeId,
                        inputStream = filePart.streamProvider(),
                        unpack = filePart.name == "jp",
                    )
                }
            }
        }
        delete<WenkuNovelRes.Id.Volume> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteVolume(
                    user = user,
                    novelId = loc.parent.novelId,
                    volumeId = loc.volumeId,
                )
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
        return volumeRepo.list("non-archived").jp
    }

    @Serializable
    data class WenkuUserVolumeDto(
        val list: List<WenkuNovelVolumeJp>,
        val novelId: String,
    )

    suspend fun getUserVolumes(user: AuthenticatedUser): WenkuUserVolumeDto {
        val novelId = "user-${user.id}"
        val volumes = volumeRepo.list(novelId).jp
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
        val favored = user?.let {
            userRepo.isUserFavoriteWenkuNovel(it.id, novelId)
        }

        val metadata = metadataRepo.get(novelId)
            ?: throwNovelNotFound()
        if (user != null) {
            metadataRepo.increaseVisited(
                userIdOrIp = user.id,
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
        if (!metadataRepo.exist(novelId))
            throwNovelNotFound()
        if (favored) {
            val total = userRepo.countFavoriteWenkuNovelByUserId(
                userId = user.id,
            )
            if (total >= 5000) {
                throwBadRequest("收藏夹已达到上限")
            }
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
        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            Operation.WenkuEdit(
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
        )
        return novelId
    }

    suspend fun updateNovel(
        user: AuthenticatedUser,
        novelId: String,
        body: MetadataCreateBody,
    ) {
        val novel = metadataRepo.get(novelId)
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

        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            Operation.WenkuEdit(
                novelId = novelId,
                old = Operation.WenkuEdit.Data(
                    title = novel.title,
                    titleZh = novel.titleZh,
                    authors = novel.authors,
                    artists = novel.artists,
                    introduction = novel.introduction,
                ),
                new = Operation.WenkuEdit.Data(
                    title = body.title,
                    titleZh = body.titleZh,
                    authors = body.authors,
                    artists = body.artists,
                    introduction = body.introduction,
                ),
            )
        )
    }

    suspend fun updateGlossary(
        user: AuthenticatedUser,
        novelId: String,
        glossary: Map<String, String>,
    ) {
        val novel = metadataRepo.get(novelId)
            ?: throwNovelNotFound()
        if (glossary == novel.glossary)
            throwBadRequest("术语表没有改变")
        metadataRepo.updateGlossary(
            novelId = novelId,
            glossary = glossary,
        )
        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            operation = Operation.WenkuEditGlossary(
                novelId = novelId,
                old = novel.glossary,
                new = glossary,
            )
        )
    }

    private fun isCacheArea(novelId: String): Boolean =
        novelId == "non-archived" || novelId.startsWith("user")

    private suspend fun validateNovelId(novelId: String) {
        if (!isCacheArea(novelId) && !metadataRepo.exist(novelId))
            throwNovelNotFound()
    }

    private fun validateVolumeId(volumeId: String) {
        if (!volumeId.endsWith("txt") && !volumeId.endsWith("epub"))
            throwBadRequest("不支持的文件格式")
    }

    suspend fun createVolume(
        user: AuthenticatedUser,
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
        unpack: Boolean,
    ) {
        validateNovelId(novelId)
        validateVolumeId(volumeId)
        if (novelId == "non-archived")
            throwBadRequest("不允许在通用缓存区上传小说")
        if (!unpack && isCacheArea(novelId))
            throwBadRequest("不允许在私人缓存区上传中文小说")

        try {
            volumeRepo.createVolume(
                novelId = novelId,
                volumeId = volumeId,
                inputStream = inputStream,
                unpack = unpack,
            )
        } catch (e: VolumeCreateException) {
            when (e) {
                is VolumeCreateException.VolumeAlreadyExist -> throwConflict("卷已经存在")
                is VolumeCreateException.VolumeUnpackFailure -> throwInternalServerError("解包失败,由于${e.cause?.message}")
            }
        }

        if (!isCacheArea(novelId)) {
            operationHistoryRepo.create(
                operator = ObjectId(user.id),
                operation = Operation.WenkuUpload(
                    novelId = novelId,
                    volumeId = volumeId,
                )
            )
            metadataRepo.notifyUpdate(novelId)
        }
    }

    suspend fun deleteVolume(
        user: AuthenticatedUser,
        novelId: String,
        volumeId: String,
    ) {
        if (!user.atLeastMaintainer() && novelId != "user-${user.id}")
            throwUnauthorized("没有权限执行操作")

        validateNovelId(novelId)
        validateVolumeId(volumeId)

        volumeRepo.deleteVolume(
            novelId = novelId,
            volumeId = volumeId,
        )
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
    ): TranslateTaskDto {
        validateNovelId(novelId)
        validateVolumeId(volumeId)

        val novel =
            if (novelId == "non-archived" || novelId.startsWith("user")) null
            else metadataRepo.get(novelId)

        val volume = volumeRepo.getVolumeJp(novelId, volumeId)
            ?: throwNotFound("卷不存在")

        val untranslatedChapterIds = mutableListOf<String>()
        val expiredChapterIds = mutableListOf<String>()
        volume.listChapter().forEach {
            if (!volume.translationExist(translatorId, it)) {
                untranslatedChapterIds.add(it)
            } else if (
                volume.getChapterGlossary(translatorId, it)?.uuid != novel?.glossaryUuid
            ) {
                expiredChapterIds.add(it)
            }
        }
        return TranslateTaskDto(
            glossaryUuid = novel?.glossaryUuid,
            glossary = novel?.glossary ?: emptyMap(),
            untranslatedChapters = untranslatedChapterIds,
            expiredChapters = expiredChapterIds,
        )
    }

    suspend fun getChapterToTranslate(
        novelId: String,
        volumeId: String,
        chapterId: String,
    ): List<String> {
        validateNovelId(novelId)
        validateVolumeId(volumeId)

        val volume = volumeRepo.getVolumeJp(novelId, volumeId)
            ?: throwNotFound("卷不存在")
        return volume.getChapter(chapterId)
            ?: throwNotFound("章节不存在")
    }

    suspend fun updateChapterTranslation(
        novelId: String,
        translatorId: TranslatorId,
        volumeId: String,
        chapterId: String,
        glossaryUuid: String?,
        paragraphsZh: List<String>,
    ): Int {
        validateNovelId(novelId)
        validateVolumeId(volumeId)

        val novel =
            if (novelId == "non-archived" || novelId.startsWith("user")) null
            else metadataRepo.get(novelId)

        if (glossaryUuid != novel?.glossaryUuid) {
            throwBadRequest("术语表uuid失效")
        }

        val volume = volumeRepo.getVolumeJp(novelId, volumeId)
            ?: throwNotFound("卷不存在")

        val jpLines = volume.getChapter(chapterId)
            ?: throwNotFound("章节不存在")

        if (jpLines.size != paragraphsZh.size)
            throwBadRequest("翻译行数不匹配")

        volume.setTranslation(
            translatorId = translatorId,
            chapterId = chapterId,
            lines = paragraphsZh,
        )
        if (glossaryUuid != null) {
            volume.setChapterGlossary(
                translatorId = translatorId,
                chapterId = chapterId,
                glossaryUuid = glossaryUuid,
                glossary = novel?.glossary ?: emptyMap(),
            )
        }

        return volume.listTranslation(
            translatorId = translatorId,
        ).size
    }

    // File
    suspend fun updateFile(
        novelId: String,
        volumeId: String,
        lang: NovelFileLangV2,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
    ): String {
        validateNovelId(novelId)
        validateVolumeId(volumeId)

        if (translations.isEmpty())
            throwBadRequest("没有设置翻译类型")

        if (lang == NovelFileLangV2.Jp)
            throwBadRequest("不支持的类型")

        val volume = volumeRepo.getVolumeJp(novelId, volumeId)
            ?: throwNotFound("卷不存在")

        val newFileName = volume.makeTranslationVolumeFile(
            lang = lang,
            translationsMode = translationsMode,
            translations = translations.distinct(),
        )
        return "${novelId}/${volumeId.encodeURLPathPart()}.unpack/$newFileName"
    }
}
