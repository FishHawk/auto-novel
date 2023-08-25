package api

import api.dto.*
import api.dto.WebNovelDto
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
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.None
import util.safeSubList
import util.toOptional
import java.nio.file.Path

@Resource("/novel")
private class WebNovelRes {
    @Resource("/list")
    data class List(
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
    data class Rank(val parent: WebNovelRes, val providerId: String)

    @Resource("/{providerId}/{novelId}")
    data class Id(val parent: WebNovelRes, val providerId: String, val novelId: String) {
        @Resource("/wenku")
        data class Wenku(val parent: Id)

        @Resource("/chapter/{chapterId}")
        data class Chapter(val parent: Id, val chapterId: String)

        @Resource("/check-update")
        data class CheckUpdate(val parent: Id) {
            @Resource("")
            data class Task(val parent: CheckUpdate, val startIndex: Int = 0, val endIndex: Int = 65536)

            @Resource("/{chapterId}")
            class Chapter(val parent: CheckUpdate, val chapterId: String)
        }

        @Resource("/translate-alt/{translatorId}")
        class Translate(val parent: Id, val translatorId: TranslatorId) {
            @Resource("")
            data class Task(val parent: Translate, val startIndex: Int = 0, val endIndex: Int = 65536)

            @Resource("/metadata")
            data class Metadata(val parent: Translate)

            @Resource("/chapter/{chapterId}")
            class Chapter(val parent: Translate, val chapterId: String)
        }

        @Resource("/file/{lang}/{type}")
        data class File(val parent: Id, val lang: NovelFileLang, val type: NovelFileType)
    }
}

private fun syosetuNovelIdMustBeLowercase(providerId: String, novelId: String) {
    if (providerId == Syosetu.id && novelId != novelId.lowercase()) {
        throw BadRequestException("成为小说家id应当小写")
    }
}

fun Route.routeWebNovel() {
    val service by inject<WebNovelApi>()

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

    authenticate(optional = true) {
        get<WebNovelRes.Id> { loc ->
            syosetuNovelIdMustBeLowercase(loc.providerId, loc.novelId)
            val jwtUser = call.jwtUserOrNull()
            val result = service.getMetadata(loc.providerId, loc.novelId, jwtUser?.username)
            call.respondResult(result)
        }
    }

    authenticate {
        put<WebNovelRes.Id> { loc ->
            val jwtUser = call.jwtUser()
            val patch = call.receive<WebNovelApi.NovelMetadataPatchBody>()
            val result = service.patchMetadata(loc.providerId, loc.novelId, patch, jwtUser.username)
            call.respondResult(result)
        }
    }

    authenticate {
        put<WebNovelRes.Id.Wenku> { loc ->
            val body = call.receive<String>()
            val result = call.requireAtLeastMaintainer { user ->
                service.updateWenkuId(loc.parent.providerId, loc.parent.novelId, body, user.username)
            }
            call.respondResult(result)
        }
        delete<WebNovelRes.Id.Wenku> { loc ->
            val result = call.requireAtLeastMaintainer { user ->
                service.deleteWenkuId(loc.parent.providerId, loc.parent.novelId, user.username)
            }
            call.respondResult(result)
        }
    }

    get<WebNovelRes.Id.Chapter> { loc ->
        syosetuNovelIdMustBeLowercase(loc.parent.providerId, loc.parent.novelId)
        val result = service.getChapter(loc.parent.providerId, loc.parent.novelId, loc.chapterId)
        call.respondResult(result)
    }

    // Check update
    get<WebNovelRes.Id.CheckUpdate.Task> { loc ->
        val result = service.getCheckUpdateTask(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            startIndex = loc.startIndex,
            endIndex = loc.endIndex,
        )
        call.respondResult(result)
    }

    post<WebNovelRes.Id.CheckUpdate.Chapter> { loc ->
        val result = service.checkChapterUpdate(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            chapterId = loc.chapterId,
        )
        call.respondResult(result)
    }

    // Translate
    get<WebNovelRes.Id.Translate.Task> { loc ->
        syosetuNovelIdMustBeLowercase(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
        )
        val result = service.getTranslateTask(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            startIndex = loc.startIndex,
            endIndex = loc.endIndex,
        )
        call.respondResult(result)
    }

    post<WebNovelRes.Id.Translate.Metadata> { loc ->
        val metadataTranslated = call.receive<WebNovelApi.TranslateMetadataUpdateBody>()
        val result = service.updateMetadataTranslation(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            body = metadataTranslated,
        )
        call.respondResult(result)
    }

    get<WebNovelRes.Id.Translate.Chapter> { loc ->
        val result = service.getChapterToTranslate(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
        )
        call.respondResult(result)
    }

    post<WebNovelRes.Id.Translate.Chapter> { loc ->
        val body = call.receive<WebNovelApi.TranslateChapterUpdateBody>()
        val result = service.updateChapterTranslation(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
            body = body,
        )
        call.respondResult(result)
    }

    put<WebNovelRes.Id.Translate.Chapter> { loc ->
        val body = call.receive<WebNovelApi.TranslateChapterUpdatePartlyBody>()
        val result = service.updateChapterPartly(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
            body = body,
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
    private val chapterRepo: WebNovelChapterRepository,
    private val fileRepo: WebNovelFileRepository,
    private val userRepo: UserRepository,
    private val patchRepo: WebNovelPatchHistoryRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
    //
    private val novelService: domain.WebNovelUpdateService,
    private val novelRepo: WebNovelMetadataRepository,
) {
    suspend fun list(
        queryString: String?,
        filterProvider: String?,
        filterType: WebNovelFilter.Type,
        filterLevel: WebNovelFilter.Level,
        filterTranslate: WebNovelFilter.Translate,
        page: Int,
        pageSize: Int,
    ): Result<PageDto<WebNovelOutlineDto>> {
        val novelPage = novelRepo.search(
            userQuery = queryString,
            filterProvider = filterProvider,
            filterType = filterType,
            filterLevel = filterLevel,
            filterTranslate = filterTranslate,
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val dtoPage = PageDto.fromPage(novelPage, pageSize) {
            WebNovelOutlineDto.fromDomain(it)
        }
        return Result.success(dtoPage)
    }

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<PageDto<WebNovelOutlineDto>> {
        val items = novelRepo.listRank(providerId, options)
            .getOrElse { return Result.failure(it) }
        val pageDto = PageDto(
            items = items.map { outline ->
                WebNovelOutlineDto.fromDomain(outline)
            },
            pageNumber = 1,
        )
        return Result.success(pageDto)
    }

    private suspend fun buildNovelDto(novel: WebNovelMetadata, username: String?): WebNovelDto {
        username?.let {
            val novelId = novel.id.toHexString()
            val favored = userRepo.isUserFavoriteWebNovel(it, novelId)
            val history = userRepo.getReaderHistory(it, novelId)
            return WebNovelDto.fromDomain(novel, favored, history?.chapterId)
        } ?: return WebNovelDto.fromDomain(novel, null, null)
    }

    suspend fun getMetadata(
        providerId: String,
        novelId: String,
        username: String?,
    ): Result<WebNovelDto> {
        val novel = novelService.getNovelAndSave(providerId, novelId)
            .getOrElse { return httpInternalServerError(it.message) }
        val dto = buildNovelDto(novel, username)
        novelRepo.increaseVisited(providerId, novelId)
        return Result.success(dto)
    }

    @Serializable
    data class NovelMetadataPatchBody(
        val title: String? = null,
        val introduction: String? = null,
        val glossary: Map<String, String>? = null,
        val toc: Map<String, String>,
    )

    suspend fun patchMetadata(
        providerId: String,
        novelId: String,
        body: NovelMetadataPatchBody,
        username: String,
    ): Result<WebNovelDto> {
        if (body.title == null &&
            body.introduction == null &&
            body.glossary == null &&
            body.toc.isEmpty()
        ) return httpInternalServerError("修改为空")

        val metadata = novelRepo.get(providerId, novelId)
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
            body.title,
        )
        val introductionChange = createTextChangeOrNull(
            metadata.introductionJp,
            metadata.introductionZh,
            body.introduction,
        )
        val glossaryChange = body.glossary?.takeIf {
            body.glossary != metadata.glossary
        }
        val tocChange = body.toc.mapNotNull { (jp, zhNew) ->
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
            glossaryChange == null &&
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
            glossaryChange = glossaryChange,
            tocChange = tocChange,
        )

        val tocZh = mutableMapOf<Int, String>()
        metadata.toc.forEachIndexed { index, item ->
            val newTitleZh = body.toc[item.titleJp]
            if (newTitleZh != null) {
                tocZh[index] = newTitleZh
            }
        }

        val novel = novelRepo.updateZh(
            providerId = providerId,
            novelId = novelId,
            titleZh = titleChange?.zhNew.toOptional(),
            introductionZh = introductionChange?.zhNew.toOptional(),
            glossary = glossaryChange.toOptional(),
            tocZh = tocZh,
        )

        val dto = buildNovelDto(novel!!, username)
        return Result.success(dto)
    }

    suspend fun updateWenkuId(
        providerId: String,
        novelId: String,
        wenkuId: String,
        username: String,
    ): Result<WebNovelDto> {
        wenkuMetadataRepo.findOne(wenkuId)
            ?: return httpNotFound("文库版不存在")
        val novel = novelRepo.updateWenkuId(providerId, novelId, wenkuId)
            ?: return httpNotFound("网页版不存在")
        val dto = buildNovelDto(novel, username)
        return Result.success(dto)
    }

    suspend fun deleteWenkuId(
        providerId: String,
        novelid: String,
        username: String,
    ): Result<WebNovelDto> {
        val novel = novelRepo.updateWenkuId(providerId, novelid, null)
            ?: return httpNotFound("网页版不存在")
        val dto = buildNovelDto(novel, username)
        return Result.success(dto)
    }

    @Serializable
    data class NovelChapterDto(
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
    ): Result<NovelChapterDto> {
        val novel = novelService.getNovelAndSave(providerId, novelId)
            .getOrElse { return httpInternalServerError(it.message) }

        val toc = novel.toc.filter { it.chapterId != null }
        val currIndex = toc.indexOfFirst { it.chapterId == chapterId }
        if (currIndex == -1) return httpInternalServerError("episode id not in toc")

        val chapter = novelService.getChapterAndSave(providerId, novelId, chapterId)
            .getOrElse { return httpInternalServerError(it.message) }

        return Result.success(
            NovelChapterDto(
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

    @Serializable
    data class CheckUpdateTaskDto(
        val chapters: Map<Int, String>,
    )

    suspend fun getCheckUpdateTask(
        providerId: String,
        novelId: String,
        startIndex: Int,
        endIndex: Int,
    ): Result<CheckUpdateTaskDto> {
        val novel = novelService.getNovelAndSave(providerId, novelId, 10)
            .getOrElse { return httpNotFound("元数据获取失败") }
        val chapters = novel.toc
            .mapNotNull { it.chapterId }
            .mapIndexed { index, it -> index to it }
            .safeSubList(startIndex, endIndex)
            .filter { (_, id) ->
                val outline = chapterRepo.getOutline(providerId, novelId, id)
                outline != null
            }
            .toMap()
        return Result.success(CheckUpdateTaskDto(chapters))
    }

    suspend fun checkChapterUpdate(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<String> {
        val local = chapterRepo.get(providerId, novelId, chapterId)
            ?: return httpNotFound("章节不存在")
        val remote = chapterRepo
            .getRemote(providerId, novelId, chapterId)
            .getOrElse { return Result.failure(it) }
        return if (remote.paragraphs == local.paragraphs) {
            Result.success("章节已是最新")
        } else {
            chapterRepo.replace(providerId, novelId, chapterId, remote.paragraphs)
            if (local.baiduParagraphs != null) {
                novelRepo.updateTranslateStateZh(providerId, novelId, TranslatorId.Baidu)
            }
            if (local.youdaoParagraphs != null) {
                novelRepo.updateTranslateStateZh(providerId, novelId, TranslatorId.Youdao)
            }
            Result.success("章节已经过期，删除翻译并更新")
        }
    }

    // Translate
    @Serializable
    data class TranslateTaskDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: List<String>,
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val untranslatedChapters: Map<Int, String>,
        val expiredChapters: Map<Int, String>,
    )

    suspend fun getTranslateTask(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
        startIndex: Int,
        endIndex: Int,
    ): Result<TranslateTaskDto> {
        val novel = novelService.getNovelAndSave(providerId, novelId, 10)
            .getOrElse { return httpNotFound("元数据获取失败") }

        val title = novel.titleJp.takeIf { novel.titleZh == null }
        val introduction = novel.introductionJp.takeIf { novel.introductionZh == null }
        val toc = novel.toc
            .mapNotNull { tocItem -> tocItem.titleJp.takeIf { tocItem.titleZh == null } }
            .distinct()


        val untranslatedChapterIds = mutableMapOf<Int, String>()
        val expiredChapterIds = mutableMapOf<Int, String>()

        novel.toc
            .mapNotNull { it.chapterId }
            .mapIndexed { index, it -> index to it }
            .safeSubList(startIndex, endIndex)
            .forEach { (index, chapterId) ->
                val outline = chapterRepo.getOutline(providerId, novelId, chapterId)
                when (translatorId) {
                    TranslatorId.Baidu -> if (outline == null || !outline.baiduExist) {
                        untranslatedChapterIds[index] = chapterId
                    } else if (outline.baiduGlossaryUuid != novel.glossaryUuid) {
                        expiredChapterIds[index] = chapterId
                    }

                    TranslatorId.Youdao -> if (outline == null || !outline.youdaoExist) {
                        untranslatedChapterIds[index] = chapterId
                    } else if (outline.youdaoGlossaryUuid != novel.glossaryUuid) {
                        expiredChapterIds[index] = chapterId
                    }

                    TranslatorId.Gpt -> if (outline == null || !outline.gptExist) {
                        untranslatedChapterIds[index] = chapterId
                    }
                }
            }

        return Result.success(
            TranslateTaskDto(
                title = title,
                introduction = introduction,
                toc = toc,
                glossaryUuid = novel.glossaryUuid,
                glossary = novel.glossary,
                untranslatedChapters = untranslatedChapterIds,
                expiredChapters = expiredChapterIds,
            )
        )
    }

    @Serializable
    data class TranslateMetadataUpdateBody(
        val title: String? = null,
        val introduction: String? = null,
        val toc: Map<String, String>,
    )

    suspend fun updateMetadataTranslation(
        providerId: String,
        novelId: String,
        body: TranslateMetadataUpdateBody,
    ): Result<Unit> {
        val metadata = novelRepo.get(providerId, novelId)
            ?: return Result.success(Unit)

        val titleZh = body.title.takeIf { metadata.titleZh == null }
        val introductionZh = body.introduction.takeIf { metadata.introductionZh == null }
        val tocZh = mutableMapOf<Int, String>()
        metadata.toc.forEachIndexed { index, item ->
            if (item.titleZh == null) {
                val newTitleZh = body.toc[item.titleJp]
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

        novelRepo.updateZh(
            providerId = providerId,
            novelId = novelId,
            titleZh = titleZh.toOptional(),
            introductionZh = introductionZh.toOptional(),
            glossary = None,
            tocZh = tocZh,
        )
        return Result.success(Unit)
    }

    @Serializable
    data class TranslateChapterDto(
        val glossary: Map<String, String>,
        val paragraphsJp: List<String>,
    )

    @Serializable
    data class TranslateStateDto(
        val jp: Long,
        val zh: Long,
    )

    suspend fun getChapterToTranslate(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
    ): Result<TranslateChapterDto> {
        val chapter = novelService.getChapterAndSave(providerId, novelId, chapterId)
            .getOrElse { return httpInternalServerError(it.message) }
        return Result.success(
            TranslateChapterDto(
                glossary = when (translatorId) {
                    TranslatorId.Baidu -> chapter.baiduGlossary
                    TranslatorId.Youdao -> chapter.youdaoGlossary
                    TranslatorId.Gpt -> emptyMap()
                },
                paragraphsJp = chapter.paragraphs,
            )
        )
    }

    @Serializable
    data class TranslateChapterUpdateBody(
        val glossaryUuid: String? = null,
        val paragraphsZh: List<String>,
    )

    suspend fun updateChapterTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        body: TranslateChapterUpdateBody,
    ): Result<TranslateStateDto> {
        val novel = novelRepo.get(providerId, novelId)
            ?: return httpNotFound("元数据不存在")
        if (
            translatorId.supportGlossary() &&
            body.glossaryUuid != novel.glossaryUuid
        ) {
            return httpBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.get(providerId, novelId, chapterId)
            ?: return httpNotFound("章节不存在")
        if (chapter.paragraphs.size != body.paragraphsZh.size) {
            return httpBadRequest("翻译文本长度不匹配")
        }

        when (translatorId) {
            TranslatorId.Baidu -> chapter.baiduParagraphs
            TranslatorId.Youdao -> chapter.youdaoParagraphs
            TranslatorId.Gpt -> chapter.gptParagraphs
        }?.let {
            return httpConflict("翻译已经存在")
        }

        if (translatorId.supportGlossary()) {
            chapterRepo.updateTranslationWithGlossary(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                translatorId = translatorId,
                glossaryUuid = novel.glossaryUuid,
                glossary = novel.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        } else {
            chapterRepo.updateTranslation(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                translatorId = translatorId,
                paragraphsZh = body.paragraphsZh,
            )
        }
        val zh = novelRepo.updateTranslateStateZh(providerId, novelId, translatorId)
        return Result.success(TranslateStateDto(jp = novel.jp, zh = zh))
    }

    @Serializable
    data class TranslateChapterUpdatePartlyBody(
        val glossaryUuid: String? = null,
        val paragraphsZh: Map<Int, String>,
    )

    suspend fun updateChapterPartly(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        body: TranslateChapterUpdatePartlyBody,
    ): Result<TranslateStateDto> {
        if (!translatorId.supportGlossary()) {
            return httpBadRequest("翻译器不支持术语表")
        }
        val novel = novelRepo.get(providerId, novelId)
            ?: return httpNotFound("元数据不存在")
        if (body.glossaryUuid != novel.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.get(providerId, novelId, chapterId)
            ?: return httpNotFound("章节不存在")

        when (translatorId) {
            TranslatorId.Baidu -> chapter.baiduParagraphs
            TranslatorId.Youdao -> chapter.youdaoParagraphs
            else -> Unit
        } ?: return httpConflict("翻译不存在")

        chapterRepo.updateTranslationWithGlossary(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            translatorId = translatorId,
            glossaryUuid = novel.glossaryUuid,
            glossary = novel.glossary,
            paragraphsZh = body.paragraphsZh,
        )
        val zh = novelRepo.updateTranslateStateZh(providerId, novelId, translatorId)
        return Result.success(TranslateStateDto(jp = novel.jp, zh = zh))
    }

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
