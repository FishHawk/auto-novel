package api

import api.model.WenkuNovelOutlineDto
import api.model.asDto
import api.plugins.*
import infra.VolumeCreateException
import infra.common.OperationHistoryRepository
import infra.user.UserRepository
import infra.model.*
import infra.user.UserFavoredWenkuRepository
import infra.wenku.WenkuNovelFilter
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
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
        val level: Int = 0,
    )

    @Resource("/{novelId}")
    class Id(val parent: WenkuNovelRes, val novelId: String) {
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
            val filename: String,
        )
    }
}

fun Route.routeWenkuNovel() {
    val service by inject<WenkuNovelApi>()

    authenticateDb(optional = true) {
        get<WenkuNovelRes.NovelList> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.list(
                    user = user,
                    queryString = loc.query?.ifBlank { null },
                    page = loc.page,
                    pageSize = loc.pageSize,
                    filterLevel = when (loc.level) {
                        1 -> WenkuNovelFilter.Level.一般向
                        2 -> WenkuNovelFilter.Level.R18
                        else -> WenkuNovelFilter.Level.全部
                    },
                )
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
    authenticateDb(optional = true) {
        put<WenkuNovelRes.Id.Translate.Chapter> { loc ->
            val user = call.authenticatedUserOrNull()

            @Serializable
            class Body(
                val glossaryUuid: String? = null,
                val paragraphsZh: List<String>,
            )

            val body = call.receive<Body>()
            call.tryRespond {
                service.updateChapterTranslation(
                    user = user,
                    novelId = loc.parent.parent.novelId,
                    translatorId = loc.parent.translatorId,
                    volumeId = loc.parent.volumeId,
                    chapterId = loc.chapterId,
                    glossaryUuid = body.glossaryUuid,
                    paragraphsZh = body.paragraphsZh,
                )
            }
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
            val encodedFilename = loc.filename.encodeURLParameter(spaceToPlus = true)
            "../../../../../../${path}?filename=${encodedFilename}"
        }
    }
}

class WenkuNovelApi(
    private val userRepo: UserRepository,
    private val metadataRepo: WenkuNovelMetadataRepository,
    private val volumeRepo: WenkuNovelVolumeRepository,
    private val favoredRepo: UserFavoredWenkuRepository,
    private val operationHistoryRepo: OperationHistoryRepository,
) {
    suspend fun list(
        user: AuthenticatedUser?,
        queryString: String?,
        page: Int,
        pageSize: Int,
        filterLevel: WenkuNovelFilter.Level,
    ): PageDto<WenkuNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)

        val filterLevelAllowed = if (user != null && user.isOldAss()) {
            filterLevel
        } else {
            WenkuNovelFilter.Level.一般向
        }

        return metadataRepo
            .search(
                userQuery = queryString,
                page = page,
                pageSize = pageSize,
                filterLevel = filterLevelAllowed,
            )
            .asDto(pageSize) { it.asDto() }
    }

    private fun throwNovelNotFound(): Nothing =
        throwNotFound("小说不存在")

    @Serializable
    data class WenkuNovelDto(
        val title: String,
        val titleZh: String,
        val cover: String,
        val authors: List<String>,
        val artists: List<String>,
        val keywords: List<String>,
        val r18: Boolean,
        val introduction: String,
        val glossary: Map<String, String>,
        val volumes: List<WenkuNovelVolume>,
        val visited: Long,
        val favored: String?,
        val favoredList: List<UserFavored>,
        val volumeZh: List<String>,
        val volumeJp: List<WenkuNovelVolumeJp>,
    )

    suspend fun getNovel(
        user: AuthenticatedUser?,
        novelId: String,
    ): WenkuNovelDto {
        val metadata = metadataRepo.get(novelId)
            ?: throwNovelNotFound()

        if (metadata.r18) {
            if (user == null) {
                throwUnauthorized("请先登录")
            } else {
                user.shouldBeOldAss()
            }
        }

        if (user != null) {
            metadataRepo.increaseVisited(
                userIdOrIp = user.id,
                novelId = novelId,
            )
        }

        val volumes = volumeRepo.list(novelId)

        val dto = WenkuNovelDto(
            title = metadata.title,
            titleZh = metadata.titleZh,
            cover = metadata.cover,
            authors = metadata.authors,
            artists = metadata.artists,
            keywords = emptyList(), // TODO:停用文库关键字，等之后重做
            r18 = metadata.r18,
            introduction = metadata.introduction,
            volumes = metadata.volumes,
            glossary = metadata.glossary,
            visited = metadata.visited,
            favored = null,
            favoredList = emptyList(),
            volumeZh = volumes.zh,
            volumeJp = volumes.jp,
        )

        return if (user == null) {
            dto
        } else {
            val favoredList = userRepo.getById(user.id)!!.favoredWenku
            val favored = favoredRepo
                .getFavoredId(user.id, novelId)
                .takeIf { favored -> favoredList.any { it.id == favored } }
            dto.copy(
                favored = favored,
                favoredList = favoredList,
            )
        }
    }

    @Serializable
    class MetadataCreateBody(
        val title: String,
        val titleZh: String,
        val cover: String,
        val authors: List<String>,
        val artists: List<String>,
        val r18: Boolean,
        val introduction: String,
        val volumes: List<WenkuNovelVolume>,
    )

    suspend fun createNovel(
        user: AuthenticatedUser,
        body: MetadataCreateBody,
    ): String {
        val novelId = metadataRepo.create(
            title = body.title,
            titleZh = body.titleZh,
            cover = body.cover,
            authors = body.authors,
            artists = body.artists,
            r18 = body.r18,
            introduction = body.introduction,
            volumes = body.volumes,
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

        val noVolumeDeleted = novel
            .volumes
            .map { it.asin }
            .all { oldAsin ->
                body.volumes.any { newVolume ->
                    newVolume.asin == oldAsin
                }
            }
        if (!noVolumeDeleted) {
            user.shouldBeAtLeast(User.Role.Maintainer)
        }

        metadataRepo.update(
            novelId = novelId,
            title = body.title,
            titleZh = body.titleZh,
            cover = body.cover,
            authors = body.authors,
            artists = body.artists,
            r18 = body.r18,
            introduction = body.introduction,
            volumes = body.volumes,
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

    private suspend fun validateNovelId(novelId: String) {
        if (!metadataRepo.exist(novelId))
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

        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            operation = Operation.WenkuUpload(
                novelId = novelId,
                volumeId = volumeId,
            )
        )
        metadataRepo.notifyUpdate(novelId)
    }

    suspend fun deleteVolume(
        user: AuthenticatedUser,
        novelId: String,
        volumeId: String,
    ) {
        user.shouldBeAtLeast(User.Role.Admin)

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
        if (translatorId == TranslatorId.Sakura) {
            throw BadRequestException("Sakura不支持浏览器翻译")
        }
        validateVolumeId(volumeId)

        val novel = metadataRepo.get(novelId)
            ?: throwNovelNotFound()
        val volume = volumeRepo.getVolume(novelId, volumeId)
            ?: throwNotFound("卷不存在")

        val untranslatedChapterIds = mutableListOf<String>()
        val expiredChapterIds = mutableListOf<String>()
        volume.listChapter().forEach {
            if (!volume.translationExist(translatorId, it)) {
                untranslatedChapterIds.add(it)
            } else if (
                volume.getChapterGlossary(translatorId, it)?.uuid != novel.glossaryUuid
            ) {
                expiredChapterIds.add(it)
            }
        }
        return TranslateTaskDto(
            glossaryUuid = novel.glossaryUuid,
            glossary = novel.glossary,
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

        val volume = volumeRepo.getVolume(novelId, volumeId)
            ?: throwNotFound("卷不存在")
        return volume.getChapter(chapterId)
            ?: throwNotFound("章节不存在")
    }

    suspend fun updateChapterTranslation(
        user: AuthenticatedUser?,
        novelId: String,
        translatorId: TranslatorId,
        volumeId: String,
        chapterId: String,
        glossaryUuid: String?,
        paragraphsZh: List<String>,
    ): Int {
        if (translatorId == TranslatorId.Sakura) {
            if (user == null) {
                throwUnauthorized("请先登录")
            } else {
                user.shouldBeAtLeast(User.Role.Trusted)
            }
        }

        validateVolumeId(volumeId)

        val novel = metadataRepo.get(novelId)
            ?: throwNovelNotFound()
        if (glossaryUuid != novel.glossaryUuid) {
            throwBadRequest("术语表uuid失效")
        }

        val volume = volumeRepo.getVolume(novelId, volumeId)
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
                glossary = novel.glossary,
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

        val volume = volumeRepo.getVolume(novelId, volumeId)
            ?: throwNotFound("卷不存在")

        val newFileName = volume.makeTranslationVolumeFile(
            lang = lang,
            translationsMode = translationsMode,
            translations = translations.distinct(),
        )
        return "files-wenku/${novelId}/${volumeId.encodeURLPathPart()}.unpack/$newFileName"
    }
}
