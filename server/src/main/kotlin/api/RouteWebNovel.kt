package api

import infra.*
import infra.model.*
import infra.provider.providers.Syosetu
import infra.web.*
import infra.wenku.WenkuNovelMetadataRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.toOptional
import java.nio.file.Path
import java.time.ZoneId

@Resource("/novel")
private class WebNovelRes {
    @Resource("/list")
    class List(
        val parent: WebNovelRes,
        val page: Int,
        val pageSize: Int = 10,
        val provider: String = "",
        val type: Int = 0,
        val level: Int = 0,
        val translate: Int = 0,
        val query: String? = null,
    )

    @Resource("/rank/{providerId}")
    class Rank(val parent: WebNovelRes, val providerId: String)

    @Resource("/favored")
    class Favored(
        val parent: WebNovelRes,
        val page: Int,
        val pageSize: Int = 10,
        val sort: FavoriteListSort,
    )

    @Resource("/read-history")
    class ReadHistory(
        val parent: WebNovelRes,
        val page: Int,
        val pageSize: Int = 10,
    )

    @Resource("/{providerId}/{novelId}")
    class Id(val parent: WebNovelRes, val providerId: String, val novelId: String) {
        @Resource("/favored")
        class Favored(val parent: Id)

        @Resource("/read-history")
        class ReadHistory(val parent: Id)

        @Resource("/wenku")
        class Wenku(val parent: Id)

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

        @Resource("/file/{lang}/{type}")
        class File(val parent: Id, val lang: NovelFileLang, val type: NovelFileType)
    }
}

private fun syosetuNovelIdMustBeLowercase(providerId: String, novelId: String) {
    if (providerId == Syosetu.id && novelId != novelId.lowercase()) {
        throw BadRequestException("成为小说家id应当小写")
    }
}

fun Route.routeWebNovel() {
    val service by inject<WebNovelApi>()

    // List
    get<WebNovelRes.List> { loc ->
        val result = service.list(
            queryString = loc.query?.ifBlank { null },
            filterProvider = loc.provider.ifEmpty { null },
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
                1 -> WebNovelFilter.Translate.AI
                else -> WebNovelFilter.Translate.全部
            },
            page = loc.page.coerceAtLeast(0),
            pageSize = loc.pageSize.coerceAtMost(20),
        )
        call.respondResult(result)
    }
    get<WebNovelRes.Rank> { loc ->
        val options = call.request.queryParameters.toMap().mapValues { it.value.first() }
        val result = service.listRank(loc.providerId, options)
        call.caching = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600 * 2))
        call.respondResult(result)
    }

    authenticate {
        get<WebNovelRes.ReadHistory> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listReadHistory(
                username = jwtUser.username,
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceAtMost(20),
            )
            call.respondResult(result)
        }
        put<WebNovelRes.Id.ReadHistory> { loc ->
            val jwtUser = call.jwtUser()
            val chapterId = call.receive<String>()
            val result = service.updateReadHistory(
                username = jwtUser.username,
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
                chapterId = chapterId,
            )
            call.respondResult(result)
        }

        get<WebNovelRes.Favored> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.listFavored(
                username = jwtUser.username,
                page = loc.page.coerceAtLeast(0),
                pageSize = loc.pageSize.coerceAtMost(20),
                sort = loc.sort,
            )
            call.respondResult(result)
        }
        put<WebNovelRes.Id.Favored> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.setFavored(
                username = jwtUser.username,
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
            )
            call.respondResult(result)
        }
        delete<WebNovelRes.Id.Favored> { loc ->
            val jwtUser = call.jwtUser()
            val result = service.removeFavored(
                username = jwtUser.username,
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
            )
            call.respondResult(result)
        }
    }

    // Get
    authenticate(optional = true) {
        get<WebNovelRes.Id> { loc ->
            syosetuNovelIdMustBeLowercase(loc.providerId, loc.novelId)
            val jwtUser = call.jwtUserOrNull()
            val result = service.getMetadata(loc.providerId, loc.novelId, jwtUser?.username)
            call.respondResult(result)
        }
        get<WebNovelRes.Id.Chapter> { loc ->
            syosetuNovelIdMustBeLowercase(loc.parent.providerId, loc.parent.novelId)
            val result = service.getChapter(loc.parent.providerId, loc.parent.novelId, loc.chapterId)
            call.respondResult(result)
        }
    }

    // Update
    authenticate {
        post<WebNovelRes.Id> { loc ->
            @Serializable
            class Body(
                val title: String? = null,
                val introduction: String? = null,
                val toc: Map<String, String>,
            )

            val jwtUser = call.jwtUser()
            val body = call.receive<Body>()
            val result = service.updateMetadata(
                providerId = loc.providerId,
                novelId = loc.novelId,
                username = jwtUser.username,
                title = body.title,
                introduction = body.introduction,
                toc = body.toc,
            )
            call.respondResult(result)
        }
        put<WebNovelRes.Id.Glossary> { loc ->
            val body = call.receive<Map<String, String>>()
            val result = service.updateGlossary(loc.parent.providerId, loc.parent.novelId, body)
            call.respondResult(result)
        }
        put<WebNovelRes.Id.Wenku> { loc ->
            val body = call.receive<String>()
            val result = call.requireAtLeastMaintainer {
                service.updateWenkuId(loc.parent.providerId, loc.parent.novelId, body)
            }
            call.respondResult(result)
        }
        delete<WebNovelRes.Id.Wenku> { loc ->
            val result = call.requireAtLeastMaintainer {
                service.deleteWenkuId(loc.parent.providerId, loc.parent.novelId)
            }
            call.respondResult(result)
        }
    }

    // Translate
    get<WebNovelRes.Id.Translate> { loc ->
        syosetuNovelIdMustBeLowercase(
            providerId = loc.parent.providerId,
            novelId = loc.parent.novelId,
        )
        val result = service.getTranslateTask(
            providerId = loc.parent.providerId,
            novelId = loc.parent.novelId,
            translatorId = loc.translatorId,
        )
        call.respondResult(result)
    }
    post<WebNovelRes.Id.Translate.Metadata> { loc ->
        @Serializable
        class Body(
            val title: String? = null,
            val introduction: String? = null,
            val toc: Map<String, String>,
        )

        val body = call.receive<Body>()
        val result = service.updateMetadataTranslation(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            title = body.title,
            introduction = body.introduction,
            toc = body.toc,
        )
        call.respondResult(result)
    }
    post<WebNovelRes.Id.Translate.CheckChapter> { loc ->
        val result = service.checkIfChapterNeedTranslate(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
            sync = loc.sync,
        )
        call.respondResult(result)
    }
    put<WebNovelRes.Id.Translate.Chapter> { loc ->
        @Serializable
        class Body(
            val glossaryUuid: String? = null,
            val paragraphsZh: List<String>,
        )

        val body = call.receive<Body>()
        val result = service.updateChapterTranslation(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
            glossaryUuid = body.glossaryUuid,
            paragraphsZh = body.paragraphsZh,
        )
        call.respondResult(result)
    }

    // File
    get<WebNovelRes.Id.File> { loc ->
        val path = service.updateFile(
            providerId = loc.parent.providerId,
            novelId = loc.parent.novelId,
            lang = loc.lang,
            type = loc.type,
        )
        if (path == null) {
            call.respondResult(httpNotFound("小说不存在"))
        } else {
            val url = "../../../../../../../files-web/${path}"
            call.respondRedirect(url)
        }
    }
}

class WebNovelApi(
    private val metadataRepo: WebNovelMetadataRepository,
    private val chapterRepo: WebNovelChapterRepository,
    private val fileRepo: WebNovelFileRepository,
    private val userRepo: UserRepository,
    private val patchRepo: WebNovelPatchHistoryRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
) {
    // List
    @Serializable
    class NovelOutlineDto(
        val providerId: String,
        val novelId: String,
        val titleJp: String,
        val titleZh: String?,
        val type: WebNovelType?,
        val attentions: List<WebNovelAttention>,
        val keywords: List<String>,
        val extra: String?,
        val total: Long,
        val jp: Long,
        val baidu: Long,
        val youdao: Long,
        val gpt: Long,
        val updateAt: Long?,
    ) {
        constructor(it: WebNovelMetadataOutline) : this(
            providerId = it.providerId,
            novelId = it.novelId,
            titleJp = it.titleJp,
            titleZh = it.titleZh,
            type = it.type,
            attentions = it.attentions,
            keywords = it.keywords,
            extra = it.extra,
            total = it.total,
            jp = it.jp,
            baidu = it.baidu,
            youdao = it.youdao,
            gpt = it.gpt,
            updateAt = it.updateAt?.epochSeconds,
        )
    }

    suspend fun list(
        queryString: String?,
        filterProvider: String?,
        filterType: WebNovelFilter.Type,
        filterLevel: WebNovelFilter.Level,
        filterTranslate: WebNovelFilter.Translate,
        page: Int,
        pageSize: Int,
    ): Result<PageDto<NovelOutlineDto>> {
        val novelPage = metadataRepo.search(
            userQuery = queryString,
            filterProvider = filterProvider,
            filterType = filterType,
            filterLevel = filterLevel,
            filterTranslate = filterTranslate,
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dtoPage = PageDto.fromPage(novelPage, pageSize) {
            NovelOutlineDto(it)
        }
        return Result.success(dtoPage)
    }

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<PageDto<NovelOutlineDto>> {
        val items = metadataRepo.listRank(providerId, options)
            .getOrElse { return Result.failure(it) }
        val pageDto = PageDto(
            items = items.map { NovelOutlineDto(it) },
            pageNumber = 1,
        )
        return Result.success(pageDto)
    }

    suspend fun listReadHistory(
        username: String,
        page: Int,
        pageSize: Int,
    ): Result<PageDto<NovelOutlineDto>> {
        val novelPage = userRepo.listReaderHistoryWebNovel(
            username = username,
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
        val novelPage = userRepo.listFavoriteWebNovel(
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

    // Get
    @Serializable
    class NovelTocItemDto(
        val titleJp: String,
        val titleZh: String?,
        val chapterId: String?,
        val createAt: Long?,
    ) {
        companion object {
            fun fromDomain(domain: WebNovelTocItem) =
                NovelTocItemDto(
                    titleJp = domain.titleJp,
                    titleZh = domain.titleZh,
                    chapterId = domain.chapterId,
                    createAt = domain.createAt?.epochSeconds,
                )
        }
    }

    @Serializable
    class NovelDto(
        val wenkuId: String?,
        val titleJp: String,
        val titleZh: String?,
        val authors: List<WebNovelAuthor>,
        val type: WebNovelType?,
        val attentions: List<WebNovelAttention>,
        val keywords: List<String>,
        val introductionJp: String,
        val introductionZh: String?,
        val glossary: Map<String, String>,
        val toc: List<NovelTocItemDto>,
        val visited: Long,
        val syncAt: Long,
        val favored: Boolean?,
        val lastReadChapterId: String?,
        val jp: Long,
        val baidu: Long,
        val youdao: Long,
        val gpt: Long,
    )

    private suspend fun buildNovelDto(novel: WebNovelMetadata, username: String?): NovelDto {
        val novelId = novel.id.toHexString()
        val favored = username?.let { userRepo.isUserFavoriteWebNovel(it, novelId) }
        val history = username?.let { userRepo.getReaderHistory(it, novelId) }
        return NovelDto(
            wenkuId = novel.wenkuId,
            titleJp = novel.titleJp,
            titleZh = novel.titleZh,
            authors = novel.authors,
            type = novel.type,
            attentions = novel.attentions,
            keywords = novel.keywords,
            introductionJp = novel.introductionJp,
            introductionZh = novel.introductionZh,
            glossary = novel.glossary,
            toc = novel.toc.map { NovelTocItemDto.fromDomain(it) },
            visited = novel.visited,
            syncAt = novel.syncAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
            favored = favored,
            lastReadChapterId = history?.chapterId,
            jp = novel.jp,
            baidu = novel.baidu,
            youdao = novel.youdao,
            gpt = novel.gpt,
        )
    }

    suspend fun getMetadata(
        providerId: String,
        novelId: String,
        username: String?,
    ): Result<NovelDto> {
        val novel = metadataRepo.getNovelAndSave(providerId, novelId)
            .getOrElse { return httpInternalServerError(it.message) }
        val dto = buildNovelDto(novel, username)
        metadataRepo.increaseVisited(providerId, novelId)
        return Result.success(dto)
    }

    @Serializable
    class ChapterDto(
        val titleJp: String,
        val titleZh: String? = null,
        val prevId: String? = null,
        val nextId: String? = null,
        val paragraphs: List<String>,
        val baiduParagraphs: List<String>? = null,
        val youdaoParagraphs: List<String>? = null,
        val gptParagraphs: List<String>? = null,
    )

    suspend fun getChapter(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<ChapterDto> {
        val novel = metadataRepo.getNovelAndSave(providerId, novelId)
            .getOrElse { return httpInternalServerError(it.message) }

        val toc = novel.toc.filter { it.chapterId != null }
        val currIndex = toc.indexOfFirst { it.chapterId == chapterId }
        if (currIndex == -1) return httpInternalServerError("章节不在目录中")

        val chapter = chapterRepo.getOrSyncRemote(providerId, novelId, chapterId)
            .getOrElse { return httpInternalServerError(it.message) }

        return Result.success(
            ChapterDto(
                titleJp = toc[currIndex].titleJp,
                titleZh = toc[currIndex].titleZh,
                prevId = toc.getOrNull(currIndex - 1)?.chapterId,
                nextId = toc.getOrNull(currIndex + 1)?.chapterId,
                paragraphs = chapter.paragraphs,
                baiduParagraphs = chapter.baiduParagraphs,
                youdaoParagraphs = chapter.youdaoParagraphs,
                gptParagraphs = chapter.gptParagraphs,
            )
        )
    }

    // Update
    suspend fun updateReadHistory(
        username: String,
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<Unit> {
        val id = metadataRepo.get(providerId, novelId)?.id
            ?: return httpNotFound("书不存在")
        userRepo.updateReadHistoryWebNovel(
            username = username,
            novelId = id.toHexString(),
            chapterId = chapterId,
        )
        return Result.success(Unit)
    }

    suspend fun setFavored(username: String, providerId: String, novelId: String): Result<Unit> {
        val id = metadataRepo.get(providerId, novelId)?.id
            ?: return httpNotFound("书不存在")
        userRepo.addFavoriteWebNovel(
            username = username,
            novelId = id.toHexString(),
        )
        return Result.success(Unit)
    }

    suspend fun removeFavored(username: String, providerId: String, novelId: String): Result<Unit> {
        val id = metadataRepo.get(providerId, novelId)?.id
            ?: return httpNotFound("书不存在")
        userRepo.removeFavoriteWebNovel(
            username = username,
            novelId = id.toHexString(),
        )
        return Result.success(Unit)
    }

    suspend fun updateMetadata(
        providerId: String,
        novelId: String,
        username: String,
        title: String?,
        introduction: String?,
        toc: Map<String, String>,
    ): Result<NovelDto> {
        val metadata = metadataRepo.get(providerId, novelId)
            ?: return httpNotFound("小说不存在")

        fun createTextChangeOrNull(
            jp: String,
            zhOld: String?,
            zhNew: String?
        ): WebNovelPatchHistory.TextChange? {
            return if (zhNew != null && zhNew != zhOld) {
                WebNovelPatchHistory.TextChange(jp, zhOld, zhNew)
            } else null
        }

        val titleChange = createTextChangeOrNull(
            metadata.titleJp,
            metadata.titleZh,
            title,
        )
        val introductionChange = createTextChangeOrNull(
            metadata.introductionJp,
            metadata.introductionZh,
            introduction,
        )
        val tocChange = toc.mapNotNull { (jp, zhNew) ->
            metadata.toc.find { it.titleJp == jp }?.let { item ->
                WebNovelPatchHistory.TextChange(
                    jp = item.titleJp,
                    zhOld = item.titleZh,
                    zhNew = zhNew
                )
            }
        }

        if (
            titleChange == null &&
            introductionChange == null &&
            tocChange.isEmpty()
        ) {
            return httpInternalServerError("修改为空")
        }

        // Add patch
        patchRepo.addPatch(
            providerId = providerId,
            novelId = novelId,
            titleJp = metadata.titleJp,
            titleZh = metadata.titleZh,
            titleChange = titleChange,
            introductionChange = introductionChange,
            glossaryChange = emptyMap(),
            tocChange = tocChange,
        )

        val tocZh = mutableMapOf<Int, String>()
        metadata.toc.forEachIndexed { index, item ->
            val newTitleZh = toc[item.titleJp]
            if (newTitleZh != null) {
                tocZh[index] = newTitleZh
            }
        }

        val novel = metadataRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            titleZh = titleChange?.zhNew.toOptional(),
            introductionZh = introductionChange?.zhNew.toOptional(),
            tocZh = tocZh,
        )

        val dto = buildNovelDto(novel!!, username)
        return Result.success(dto)
    }

    suspend fun updateGlossary(
        providerId: String,
        novelId: String,
        glossary: Map<String, String>,
    ): Result<Unit> {
        val metadata = metadataRepo.get(providerId, novelId)
            ?: return httpNotFound("小说不存在")

        if (metadata.glossary == glossary)
            return httpInternalServerError("修改为空")

        metadataRepo.updateGlossary(providerId, novelId, glossary)
        return Result.success(Unit)
    }

    suspend fun updateWenkuId(
        providerId: String,
        novelId: String,
        wenkuId: String,
    ): Result<Unit> {
        wenkuMetadataRepo.get(wenkuId)
            ?: return httpNotFound("文库版不存在")
        val updateResult = metadataRepo.updateWenkuId(providerId, novelId, wenkuId)
        if (updateResult.matchedCount == 0L) {
            return httpNotFound("网页版不存在")
        }
        return Result.success(Unit)
    }

    suspend fun deleteWenkuId(
        providerId: String,
        novelid: String,
    ): Result<Unit> {
        val updateResult = metadataRepo.updateWenkuId(providerId, novelid, null)
        if (updateResult.matchedCount == 0L) {
            return httpNotFound("网页版不存在")
        }
        return Result.success(Unit)
    }

    // Translate
    @Serializable
    class TranslateTaskDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: List<String>,
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val chapters: List<ChapterIdWithState>,
    ) {
        @Serializable
        class ChapterIdWithState(
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
    ): Result<TranslateTaskDto> {
        val novel = metadataRepo.getNovelAndSave(providerId, novelId, 10)
            .getOrElse { return httpNotFound("元数据获取失败") }

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
                } else if (chapterTranslationOutline.glossaryUuid == novel.glossaryUuid) {
                    TranslateTaskDto.ChapterState.Translated
                } else {
                    TranslateTaskDto.ChapterState.TranslatedAndExpired
                }
                TranslateTaskDto.ChapterIdWithState(
                    id = chapterId,
                    state = chapterState,
                )
            }

        return Result.success(
            TranslateTaskDto(
                title = title,
                introduction = introduction,
                toc = toc,
                glossaryUuid = novel.glossaryUuid,
                glossary = novel.glossary,
                chapters = chapters,
            )
        )
    }

    suspend fun updateMetadataTranslation(
        providerId: String,
        novelId: String,
        title: String?,
        introduction: String?,
        toc: Map<String, String>,
    ): Result<Unit> {
        val metadata = metadataRepo.get(providerId, novelId)
            ?: return Result.success(Unit)

        val titleZh = title.takeIf { metadata.titleZh == null }
        val introductionZh = introduction.takeIf { metadata.introductionZh == null }
        val tocZh = mutableMapOf<Int, String>()
        metadata.toc.forEachIndexed { index, item ->
            if (item.titleZh == null) {
                val newTitleZh = toc[item.titleJp]
                if (newTitleZh != null) {
                    tocZh[index] = newTitleZh
                }
            }
        }

        if (titleZh == null &&
            introductionZh == null &&
            tocZh.isEmpty()
        ) {
            return Result.success(Unit)
        }

        metadataRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            titleZh = titleZh.toOptional(),
            introductionZh = introductionZh.toOptional(),
            tocZh = tocZh,
        )
        return Result.success(Unit)
    }

    suspend fun checkIfChapterNeedTranslate(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
        chapterId: String,
        sync: Boolean,
    ): Result<List<String>> {
        val novel = metadataRepo.get(providerId, novelId)
            ?: return httpNotFound("元数据不存在")

        val chapter = chapterRepo.getOrSyncRemote(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            forceSync = sync,
        ).getOrElse {
            return httpInternalServerError(it.message)
        }

        val (oldGlossaryUuid, translated) = chapter.run {
            when (translatorId) {
                TranslatorId.Baidu -> Pair(baiduGlossaryUuid, baiduParagraphs)
                TranslatorId.Youdao -> Pair(youdaoGlossaryUuid, youdaoParagraphs)
                TranslatorId.Gpt -> Pair(gptGlossaryUuid, gptParagraphs)
            }
        }

        return if (
            translated != null &&
            oldGlossaryUuid == novel.glossaryUuid
        ) {
            Result.success(emptyList())
        } else {
            Result.success(chapter.paragraphs)
        }
    }

    @Serializable
    class TranslateStateDto(val jp: Long, val zh: Long)

    suspend fun updateChapterTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossaryUuid: String?,
        paragraphsZh: List<String>,
    ): Result<TranslateStateDto> {
        val novel = metadataRepo.get(providerId, novelId)
            ?: return httpNotFound("元数据不存在")
        if (glossaryUuid != novel.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.get(providerId, novelId, chapterId)
            ?: return httpNotFound("章节不存在")
        if (chapter.paragraphs.size != paragraphsZh.size) {
            return httpBadRequest("翻译文本长度不匹配")
        }

        val zh = chapterRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            translatorId = translatorId,
            glossary = novel.glossaryUuid?.let { Glossary(it, novel.glossary) },
            paragraphsZh = paragraphsZh,
        )
        return Result.success(TranslateStateDto(jp = novel.jp, zh = zh))
    }

    // File
    suspend fun updateFile(
        providerId: String,
        novelId: String,
        lang: NovelFileLang,
        type: NovelFileType,
    ): Path? {
        return fileRepo.makeFile(
            providerId = providerId,
            novelId = novelId,
            lang = lang,
            type = type,
        )
    }
}
