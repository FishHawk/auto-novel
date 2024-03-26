package api

import api.model.WebNovelOutlineDto
import api.model.asDto
import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import api.plugins.authenticatedUserOrNull
import domain.entity.*
import infra.common.OperationHistoryRepository
import infra.user.UserFavoredWebRepository
import infra.user.UserReadHistoryWebRepository
import infra.user.UserRepository
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelFileRepository
import infra.web.WebNovelFilter
import infra.web.WebNovelMetadataRepository
import infra.web.providers.NovelIdShouldBeReplacedException
import infra.web.providers.Syosetu
import infra.wenku.WenkuNovelMetadataRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject

@Resource("/novel")
private class WebNovelRes {
    @Resource("")
    class List(
        val parent: WebNovelRes,
        val page: Int,
        val pageSize: Int,
        val provider: String = "",
        val type: Int = 0,
        val level: Int = 0,
        val translate: Int = 0,
        val sort: Int = 0,
        val query: String? = null,
    )

    @Resource("/rank/{providerId}")
    class Rank(val parent: WebNovelRes, val providerId: String)

    @Resource("/{providerId}/{novelId}")
    class Id(val parent: WebNovelRes, val providerId: String, val novelId: String) {
        @Resource("/glossary")
        class Glossary(val parent: Id)

        @Resource("/chapter/{chapterId}")
        class Chapter(val parent: Id, val chapterId: String)

        @Resource("/translate/{translatorId}")
        class Translate(val parent: Id, val translatorId: TranslatorId) {
            @Resource("/metadata")
            class Metadata(val parent: Translate)

            @Resource("/check-chapter/{chapterId}")
            class CheckChapter(val parent: Translate, val chapterId: String, val sync: Boolean)

            @Resource("/chapter/{chapterId}")
            class Chapter(val parent: Translate, val chapterId: String)
        }

        @Resource("/file")
        class File(
            val parent: Id,
            val mode: NovelFileMode,
            val translationsMode: NovelFileTranslationsMode,
            val translations: kotlin.collections.List<TranslatorId> = emptyList(),
            val type: NovelFileType,
            val filename: String,
        )
    }
}

fun Route.routeWebNovel() {
    val service by inject<WebNovelApi>()

    authenticateDb(optional = true) {
        get<WebNovelRes.List> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.list(
                    user = user,
                    queryString = loc.query?.ifBlank { null },
                    filterProvider = loc.provider,
                    filterType = when (loc.type) {
                        1 -> WebNovelFilter.Type.连载中
                        2 -> WebNovelFilter.Type.已完结
                        3 -> WebNovelFilter.Type.短篇
                        else -> WebNovelFilter.Type.全部
                    },
                    filterLevel = when (loc.level) {
                        1 -> WebNovelFilter.Level.一般向
                        2 -> WebNovelFilter.Level.R18
                        else -> WebNovelFilter.Level.全部
                    },
                    filterTranslate = when (loc.translate) {
                        1 -> WebNovelFilter.Translate.GPT3
                        2 -> WebNovelFilter.Translate.Sakura
                        else -> WebNovelFilter.Translate.全部
                    },
                    filterSort = when (loc.sort) {
                        1 -> WebNovelFilter.Sort.点击
                        2 -> WebNovelFilter.Sort.相关
                        else -> WebNovelFilter.Sort.更新
                    },
                    page = loc.page,
                    pageSize = loc.pageSize,
                )
            }
        }
    }
    get<WebNovelRes.Rank> { loc ->
        val options = call.request.queryParameters.toMap().mapValues { it.value.first() }
        call.caching = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600 * 2))
        call.tryRespond {
            service.listRank(providerId = loc.providerId, options = options)
        }
    }

    // Get
    authenticateDb(optional = true) {
        get<WebNovelRes.Id> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.getMetadata(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                )
            }
        }
    }
    get<WebNovelRes.Id.Chapter> { loc ->
        call.tryRespond {
            service.getChapter(
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
                chapterId = loc.chapterId,
            )
        }
    }

    // Update
    authenticateDb {
        post<WebNovelRes.Id> { loc ->
            @Serializable
            class Body(
                val title: String,
                val introduction: String,
                val wenkuId: String,
                val toc: Map<String, String>,
            )

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.updateMetadata(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                    title = body.title,
                    introduction = body.introduction,
                    wenkuId = body.wenkuId,
                    toc = body.toc,
                )
            }
        }
        put<WebNovelRes.Id.Glossary> { loc ->
            val user = call.authenticatedUser()
            val body = call.receive<Map<String, String>>()
            call.tryRespond {
                service.updateGlossary(
                    user = user,
                    providerId = loc.parent.providerId,
                    novelId = loc.parent.novelId,
                    glossary = body,
                )
            }
        }
    }

    // Translate
    get<WebNovelRes.Id.Translate> { loc ->
        call.tryRespond {
            service.getTranslateTask(
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
                translatorId = loc.translatorId,
            )
        }
    }
    post<WebNovelRes.Id.Translate.Metadata> { loc ->
        @Serializable
        class Body(
            val title: String? = null,
            val introduction: String? = null,
            val toc: Map<String, String>,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.updateMetadataTranslation(
                providerId = loc.parent.parent.providerId,
                novelId = loc.parent.parent.novelId,
                title = body.title,
                introduction = body.introduction,
                toc = body.toc,
            )
        }
    }
    post<WebNovelRes.Id.Translate.CheckChapter> { loc ->
        call.tryRespond {
            service.checkIfChapterNeedTranslate(
                providerId = loc.parent.parent.providerId,
                novelId = loc.parent.parent.novelId,
                translatorId = loc.parent.translatorId,
                chapterId = loc.chapterId,
                sync = loc.sync,
            )
        }
    }
    put<WebNovelRes.Id.Translate.Chapter> { loc ->
        @Serializable
        class Body(
            val glossaryUuid: String? = null,
            val paragraphsZh: List<String>,
            val sakuraVersion: String? = null,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.updateChapterTranslation(
                providerId = loc.parent.parent.providerId,
                novelId = loc.parent.parent.novelId,
                translatorId = loc.parent.translatorId,
                chapterId = loc.chapterId,
                glossaryUuid = body.glossaryUuid,
                paragraphsZh = body.paragraphsZh,
                sakuraVersion = body.sakuraVersion,
            )
        }
    }

    // File
    get<WebNovelRes.Id.File> { loc ->
        call.tryRespondRedirect {
            val path = service.updateFile(
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
                mode = loc.mode,
                translationsMode = loc.translationsMode,
                translations = loc.translations,
                type = loc.type,
            )
            val encodedFilename = loc.filename.encodeURLParameter(spaceToPlus = true)
            "../../../../../../../files-web/${path}?filename=${encodedFilename}"
        }
    }
}

class WebNovelApi(
    private val metadataRepo: WebNovelMetadataRepository,
    private val chapterRepo: WebNovelChapterRepository,
    private val fileRepo: WebNovelFileRepository,
    private val userRepo: UserRepository,
    private val favoredRepo: UserFavoredWebRepository,
    private val historyRepo: UserReadHistoryWebRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
    private val operationHistoryRepo: OperationHistoryRepository,
) {
    suspend fun list(
        user: AuthenticatedUser?,
        queryString: String?,
        filterProvider: String,
        filterType: WebNovelFilter.Type,
        filterLevel: WebNovelFilter.Level,
        filterTranslate: WebNovelFilter.Translate,
        filterSort: WebNovelFilter.Sort,
        page: Int,
        pageSize: Int,
    ): Page<WebNovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)

        val filterProviderParsed = if (filterProvider.isEmpty()) {
            return emptyPage()
        } else {
            filterProvider.split(",")
        }

        val filterLevelAllowed = if (user != null && user.isOldAss()) {
            filterLevel
        } else {
            WebNovelFilter.Level.一般向
        }

        return metadataRepo
            .search(
                userQuery = queryString,
                filterProvider = filterProviderParsed,
                filterType = filterType,
                filterLevel = filterLevelAllowed,
                filterTranslate = filterTranslate,
                filterSort = filterSort,
                page = page,
                pageSize = pageSize,
            )
            .map { it.asDto() }
    }

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Page<WebNovelOutlineDto> {
        return metadataRepo
            .listRank(providerId, options)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }
            .map { it.asDto() }
    }

    // Get
    private fun throwNovelNotFound(): Nothing =
        throwNotFound("小说不存在")

    private fun validateId(providerId: String, novelId: String) {
        if (providerId == Syosetu.id && novelId != novelId.lowercase()) {
            throw BadRequestException("成为小说家id应当小写")
        }
    }

    @Serializable
    data class NovelTocItemDto(
        val titleJp: String,
        val titleZh: String?,
        val chapterId: String?,
        val createAt: Long?,
    )

    private fun WebNovelTocItem.asDto() =
        NovelTocItemDto(
            titleJp = titleJp,
            titleZh = titleZh,
            chapterId = chapterId,
            createAt = createAt?.epochSeconds,
        )

    @Serializable
    data class NovelDto(
        val wenkuId: String?,
        val titleJp: String,
        val titleZh: String?,
        val authors: List<WebNovelAuthor>,
        val type: WebNovelType?,
        val attentions: List<WebNovelAttention>,
        val keywords: List<String>,
        val points: Int?,
        val totalCharacters: Int?,
        val introductionJp: String,
        val introductionZh: String?,
        val glossary: Map<String, String>,
        val toc: List<NovelTocItemDto>,
        val visited: Long,
        val syncAt: Long,
        val favored: String?,
        val favoredList: List<UserFavored>,
        val lastReadChapterId: String?,
        val jp: Long,
        val baidu: Long,
        val youdao: Long,
        val gpt: Long,
        val sakura: Long,
    )

    private suspend fun buildNovelDto(
        novel: WebNovelMetadata,
        user: AuthenticatedUser?,
    ): NovelDto {
        val dto = NovelDto(
            wenkuId = novel.wenkuId,
            titleJp = novel.titleJp,
            titleZh = novel.titleZh,
            authors = novel.authors,
            type = novel.type,
            attentions = novel.attentions,
            keywords = novel.keywords,
            points = novel.points,
            totalCharacters = novel.totalCharacters,
            introductionJp = novel.introductionJp,
            introductionZh = novel.introductionZh,
            glossary = novel.glossary,
            toc = novel.toc.map { it.asDto() },
            visited = novel.visited,
            syncAt = novel.syncAt.epochSeconds,
            favored = null,
            favoredList = emptyList(),
            lastReadChapterId = null,
            jp = novel.jp,
            baidu = novel.baidu,
            youdao = novel.youdao,
            gpt = novel.gpt,
            sakura = novel.sakura,
        )
        return if (user == null) {
            dto
        } else {
            val novelId = novel.id.toHexString()
            val favoredList = userRepo.getById(user.id)!!.favoredWeb
            val favored = favoredRepo
                .getFavoredId(user.id, novelId)
                .takeIf { favored -> favoredList.any { it.id == favored } }
            val history = historyRepo.getReaderHistory(user.id, novelId)
            dto.copy(
                favored = favored,
                favoredList = favoredList,
                lastReadChapterId = history?.chapterId,
            )
        }
    }

    suspend fun getMetadata(
        user: AuthenticatedUser?,
        providerId: String,
        novelId: String,
    ): NovelDto {
        validateId(providerId, novelId)
        val novel = metadataRepo.getNovelAndSave(providerId, novelId)
            .getOrElse {
                if (it is NovelIdShouldBeReplacedException) {
                    throwBadRequest(it.message!!)
                } else {
                    throwInternalServerError("从源站获取失败:" + it.message)
                }
            }
        val dto = buildNovelDto(novel, user)
        if (user != null) {
            metadataRepo.increaseVisited(
                userIdOrIp = user.id,
                providerId = novel.providerId,
                novelId = novel.novelId,
            )
        }
        return dto
    }

    @Serializable
    data class ChapterDto(
        val titleJp: String,
        val titleZh: String?,
        val prevId: String?,
        val nextId: String?,
        val paragraphs: List<String>,
        val baiduParagraphs: List<String>?,
        val youdaoParagraphs: List<String>?,
        val gptParagraphs: List<String>?,
        val sakuraParagraphs: List<String>?,
    )

    suspend fun getChapter(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): ChapterDto {
        validateId(providerId, novelId)
        val novel = metadataRepo.getNovelAndSave(providerId, novelId)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        val toc = novel.toc.filter { it.chapterId != null }
        val currIndex = toc.indexOfFirst { it.chapterId == chapterId }
        if (currIndex == -1) throwInternalServerError("章节不在目录中")

        val chapter = chapterRepo.getOrSyncRemote(providerId, novelId, chapterId)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        return ChapterDto(
            titleJp = toc[currIndex].titleJp,
            titleZh = toc[currIndex].titleZh,
            prevId = toc.getOrNull(currIndex - 1)?.chapterId,
            nextId = toc.getOrNull(currIndex + 1)?.chapterId,
            paragraphs = chapter.paragraphs,
            baiduParagraphs = chapter.baiduParagraphs,
            youdaoParagraphs = chapter.youdaoParagraphs,
            gptParagraphs = chapter.gptParagraphs,
            sakuraParagraphs = chapter.sakuraParagraphs,
        )
    }

    // Update
    suspend fun updateMetadata(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        title: String,
        introduction: String,
        wenkuId: String,
        toc: Map<String, String>,
    ) {
        if (wenkuId.isNotBlank() && wenkuMetadataRepo.get(wenkuId) == null) {
            throwNotFound("文库版不存在")
        }

        val metadata = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()

        val tocZh = mutableMapOf<Int, String>()
        val tocRecord = mutableListOf<Operation.WebEdit.Toc>()
        metadata.toc.forEachIndexed { index, item ->
            val newTitleZh = toc[item.titleJp]
            if (newTitleZh != null && newTitleZh != item.titleZh) {
                tocZh[index] = newTitleZh
                tocRecord.add(
                    Operation.WebEdit.Toc(
                        jp = item.titleJp,
                        old = item.titleZh,
                        new = newTitleZh,
                    )
                )
            }
        }

        metadataRepo.updateWenkuId(
            providerId = providerId,
            novelId = novelId,
            wenkuId = wenkuId.takeIf { it.isNotBlank() },
        )
        metadataRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            titleZh = title.takeIf { it.isNotBlank() },
            introductionZh = introduction.takeIf { it.isNotBlank() },
            tocZh = tocZh,
        )

        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            operation = Operation.WebEdit(
                providerId = providerId,
                novelId = novelId,
                old = Operation.WebEdit.Data(
                    titleZh = metadata.titleZh,
                    introductionZh = metadata.introductionZh,
                ),
                new = Operation.WebEdit.Data(
                    titleZh = title,
                    introductionZh = introduction,
                ),
                toc = tocRecord,
            )
        )
    }

    suspend fun updateGlossary(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        glossary: Map<String, String>,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        if (novel.glossary == glossary)
            throwBadRequest("修改为空")
        metadataRepo.updateGlossary(
            providerId = providerId,
            novelId = novelId,
            glossary = glossary,
        )
        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            operation = Operation.WebEditGlossary(
                providerId = providerId,
                novelId = novelId,
                old = novel.glossary,
                new = glossary,
            )
        )
    }

    // Translate
    @Serializable
    data class TranslateTaskDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: List<String>,
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val chapters: List<ChapterIdWithState>,
    ) {
        @Serializable
        data class ChapterIdWithState(
            val id: String,
            val state: ChapterState,
        )

        @Serializable
        enum class ChapterState {
            @SerialName("untranslated")
            Untranslated,

            @SerialName("translated")
            Translated,

            @SerialName("expired")
            TranslatedAndExpired,
        }
    }

    suspend fun getTranslateTask(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
    ): TranslateTaskDto {
        validateId(providerId, novelId)

        val novel = metadataRepo.getNovelAndSave(providerId, novelId, 10)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        val title = novel.titleJp.takeIf { novel.titleZh == null }
        val introduction = novel.introductionJp.takeIf { novel.introductionZh == null }
        val toc = novel.toc
            .mapNotNull { tocItem -> tocItem.titleJp.takeIf { tocItem.titleZh == null } }
            .distinct()

        val chapterTranslationOutlines = chapterRepo.getTranslationOutlines(
            providerId = providerId,
            novelId = novelId,
            translatorId = translatorId,
        )
        val chapters = novel.toc
            .mapNotNull { it.chapterId }
            .map { chapterId ->
                val chapterTranslationOutline = chapterTranslationOutlines.find {
                    it.chapterId == chapterId
                }
                val chapterState = if (chapterTranslationOutline?.translated != true) {
                    TranslateTaskDto.ChapterState.Untranslated
                } else if (
                    chapterTranslationOutline.glossaryUuid != novel.glossaryUuid ||
                    (translatorId == TranslatorId.Sakura && chapterTranslationOutline.sakuraVersion != "0.9")
                ) {
                    TranslateTaskDto.ChapterState.TranslatedAndExpired
                } else {
                    TranslateTaskDto.ChapterState.Translated
                }
                TranslateTaskDto.ChapterIdWithState(
                    id = chapterId,
                    state = chapterState,
                )
            }

        return TranslateTaskDto(
            title = title,
            introduction = introduction,
            toc = toc,
            glossaryUuid = novel.glossaryUuid,
            glossary = novel.glossary,
            chapters = chapters,
        )
    }

    suspend fun updateMetadataTranslation(
        providerId: String,
        novelId: String,
        title: String?,
        introduction: String?,
        toc: Map<String, String>,
    ) {
        val metadata = metadataRepo.get(providerId, novelId)
            ?: return

        val tocZh = mutableMapOf<Int, String>()
        metadata.toc.forEachIndexed { index, item ->
            val newTitleZh = toc[item.titleJp]
            if (newTitleZh != null) {
                tocZh[index] = newTitleZh
            }
        }

        if (title == null &&
            introduction == null &&
            tocZh.isEmpty()
        ) return

        metadataRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            titleZh = title ?: metadata.titleZh,
            introductionZh = introduction ?: metadata.introductionZh,
            tocZh = tocZh,
        )
    }

    suspend fun checkIfChapterNeedTranslate(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
        chapterId: String,
        sync: Boolean,
    ): List<String> {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()

        val chapter = chapterRepo.getOrSyncRemote(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            forceSync = sync,
        ).getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        val (oldGlossaryUuid, translated) = chapter.run {
            when (translatorId) {
                TranslatorId.Baidu -> Pair(baiduGlossaryUuid, baiduParagraphs)
                TranslatorId.Youdao -> Pair(youdaoGlossaryUuid, youdaoParagraphs)
                TranslatorId.Gpt -> Pair(gptGlossaryUuid, gptParagraphs)
                TranslatorId.Sakura -> Pair(sakuraGlossaryUuid, sakuraParagraphs)
            }
        }

        return if (
            translated != null &&
            oldGlossaryUuid == novel.glossaryUuid &&
            (translatorId != TranslatorId.Sakura || chapter.sakuraVersion == "0.9")
        ) {
            emptyList()
        } else {
            chapter.paragraphs
        }
    }

    @Serializable
    data class TranslateStateDto(val jp: Long, val zh: Long)

    suspend fun updateChapterTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossaryUuid: String?,
        paragraphsZh: List<String>,
        sakuraVersion: String?,
    ): TranslateStateDto {
        if (translatorId == TranslatorId.Sakura && sakuraVersion != "0.9") {
            throwBadRequest("旧版本Sakura不再允许上传")
        }

        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        if (glossaryUuid != novel.glossaryUuid) {
            throwBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.get(providerId, novelId, chapterId)
            ?: throwNotFound("章节不存在")
        if (chapter.paragraphs.size != paragraphsZh.size) {
            throwBadRequest("翻译文本长度不匹配")
        }

        val zh = chapterRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            translatorId = translatorId,
            glossary = novel.glossaryUuid?.let { Glossary(it, novel.glossary) },
            paragraphsZh = paragraphsZh,
        )
        return TranslateStateDto(jp = novel.jp, zh = zh)
    }

    // File
    suspend fun updateFile(
        providerId: String,
        novelId: String,
        mode: NovelFileMode,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
        type: NovelFileType,
    ): String {
        return fileRepo.makeFile(
            providerId = providerId,
            novelId = novelId,
            mode = mode,
            translationsMode = translationsMode,
            translations = translations.distinct(),
            type = type,
        ) ?: throwNovelNotFound()
    }
}
