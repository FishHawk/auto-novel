package api

import data.*
import data.web.WebNovelIndexRepository
import data.web.*
import data.wenku.WenkuNovelMetadataRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
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
import java.time.ZoneId

@Resource("/novel")
private class WebNovel {
    @Resource("/list")
    data class List(
        val parent: WebNovel,
        val page: Int,
        val pageSize: Int = 10,
        val provider: String = "",
        val query: String? = null,
    )

    @Resource("/rank/{providerId}")
    data class Rank(val parent: WebNovel, val providerId: String)

    @Resource("/{providerId}/{novelId}")
    data class Id(val parent: WebNovel, val providerId: String, val novelId: String) {
        @Resource("/wenku")
        data class Wenku(val parent: Id)

        @Resource("/chapter/{chapterId}")
        data class Chapter(val parent: Id, val chapterId: String)

        @Resource("/translate/{translatorId}")
        class Translate(val parent: Id, val translatorId: String) {
            @Resource("/metadata")
            data class Metadata(val parent: Translate, val startIndex: Int = 0, val endIndex: Int = 65536)

            @Resource("/chapter/{chapterId}")
            class Chapter(val parent: Translate, val chapterId: String)
        }

        @Resource("/file/{lang}/{type}")
        data class File(val parent: Id, val lang: NovelFileLang, val type: NovelFileType)
    }
}

fun Route.routeWebNovel() {
    val service by inject<WebNovelService>()

    get<WebNovel.List> { loc ->
        val result = service.list(
            queryString = loc.query?.ifBlank { null },
            providerId = loc.provider.ifEmpty { null },
            page = loc.page.coerceAtLeast(0),
            pageSize = loc.pageSize.coerceAtMost(20),
        )
        call.respondResult(result)
    }

    get<WebNovel.Rank> { loc ->
        val options = call.request.queryParameters.toMap().mapValues { it.value.first() }
        val result = service.listRank(loc.providerId, options)
        call.caching = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600 * 2))
        call.respondResult(result)
    }

    authenticate(optional = true) {
        get<WebNovel.Id> { loc ->
            val jwtUser = call.jwtUserOrNull()
            val result = service.getMetadata(loc.providerId, loc.novelId, jwtUser?.username)
            call.respondResult(result)
        }
    }

    authenticate {
        put<WebNovel.Id> { loc ->
            val jwtUser = call.jwtUser()
            val patch = call.receive<WebNovelService.NovelMetadataPatchBody>()
            val result = service.patchMetadata(loc.providerId, loc.novelId, patch, jwtUser.username)
            call.respondResult(result)
        }
    }

    authenticate {
        put<WebNovel.Id.Wenku> { loc ->
            val body = call.receive<String>()
            val result = call.requireAtLeastMaintainer { user ->
                service.updateWenkuId(loc.parent.providerId, loc.parent.novelId, body, user.username)
            }
            call.respondResult(result)
        }
        delete<WebNovel.Id.Wenku> { loc ->
            val result = call.requireAtLeastMaintainer { user ->
                service.deleteWenkuId(loc.parent.providerId, loc.parent.novelId, user.username)
            }
            call.respondResult(result)
        }
    }

    get<WebNovel.Id.Chapter> { loc ->
        val result = service.getChapter(loc.parent.providerId, loc.parent.novelId, loc.chapterId)
        call.respondResult(result)
    }

    // Translator
    get<WebNovel.Id.Translate.Metadata> { loc ->
        val result = service.getMetadataToTranslate(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            startIndex = loc.startIndex,
            endIndex = loc.endIndex,
        )
        call.respondResult(result)
    }

    post<WebNovel.Id.Translate.Metadata> { loc ->
        val metadataTranslated = call.receive<WebNovelService.TranslateMetadataUpdateBody>()
        val result = service.updateMetadataTranslation(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            body = metadataTranslated,
        )
        call.respondResult(result)
    }

    get<WebNovel.Id.Translate.Chapter> { loc ->
        val result = service.getChapterToTranslate(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
        )
        call.respondResult(result)
    }

    post<WebNovel.Id.Translate.Chapter> { loc ->
        val body = call.receive<WebNovelService.TranslateChapterUpdateBody>()
        val result = service.updateChapterTranslation(
            providerId = loc.parent.parent.providerId,
            novelId = loc.parent.parent.novelId,
            translatorId = loc.parent.translatorId,
            chapterId = loc.chapterId,
            body = body,
        )
        call.respondResult(result)
    }

    put<WebNovel.Id.Translate.Chapter> { loc ->
        val body = call.receive<WebNovelService.TranslateChapterUpdatePartlyBody>()
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
    get<WebNovel.Id.File> { loc ->
        val result = service.updateFile(
            providerId = loc.parent.providerId,
            novelId = loc.parent.novelId,
            lang = loc.lang,
            type = loc.type,
        )
        result.onSuccess {
            val url = "../../../../../../../files-web/${it}"
            call.respondRedirect(url)
        }.onFailure {
            call.respondResult(result)
        }
    }
}

class WebNovelService(
    private val metadataRepo: WebNovelMetadataRepository,
    private val chapterRepo: WebChapterRepository,
    private val fileRepo: WebNovelFileRepository,
    private val userRepo: UserRepository,
    private val patchRepo: WebNovelPatchHistoryRepository,
    private val indexRepo: WebNovelIndexRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
) {
    @Serializable
    data class NovelListPageDto(
        val pageNumber: Long,
        val items: List<ItemDto>,
    ) {
        @Serializable
        data class ItemDto(
            val providerId: String,
            val novelId: String,
            val titleJp: String,
            val titleZh: String?,
            val total: Int,
            val count: Long,
            val countBaidu: Long,
            val countYoudao: Long,
        )
    }

    suspend fun list(
        queryString: String?,
        providerId: String?,
        page: Int,
        pageSize: Int,
    ): Result<NovelListPageDto> {
        val esPage = indexRepo.search(
            queryString = queryString,
            providerId = providerId,
            page = page.coerceAtLeast(0),
            pageSize = pageSize,
        )
        val items = esPage.items.map {
            NovelListPageDto.ItemDto(
                providerId = it.providerId,
                novelId = it.novelId,
                titleJp = it.titleJp,
                titleZh = it.titleZh,
                total = metadataRepo.findOne(
                    it.providerId,
                    it.novelId
                )!!.toc.count { it.chapterId != null },
                count = chapterRepo.count(it.providerId, it.novelId),
                countBaidu = chapterRepo.countBaidu(it.providerId, it.novelId),
                countYoudao = chapterRepo.countYoudao(it.providerId, it.novelId),
            )
        }
        val dto = NovelListPageDto(
            pageNumber = (esPage.total / pageSize) + 1,
            items = items,
        )
        return Result.success(dto)
    }


    @Serializable
    data class NovelRankPageDto(
        val pageNumber: Long,
        val items: List<ItemDto>,
    ) {
        @Serializable
        data class ItemDto(
            val providerId: String,
            val novelId: String,
            val titleJp: String,
            val titleZh: String?,
            val extra: String,
        )
    }

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<NovelRankPageDto> {
        return metadataRepo.listRank(
            providerId = providerId,
            options = options,
        ).map {
            NovelRankPageDto(
                pageNumber = 1,
                items = it.map {
                    NovelRankPageDto.ItemDto(
                        providerId = providerId,
                        novelId = it.novelId,
                        titleJp = it.titleJp,
                        titleZh = it.titleZh,
                        extra = it.extra,
                    )
                }
            )
        }
    }

    @Serializable
    data class NovelMetadataDto(
        val wenkuId: String?,
        val titleJp: String,
        val titleZh: String?,
        val authors: List<Author>,
        val introductionJp: String,
        val introductionZh: String?,
        val glossary: Map<String, String>,
        val toc: List<TocItem>,
        val visited: Long,
        val syncAt: Long,
        val favored: Boolean?,
        val translateState: TranslateState,
    ) {
        @Serializable
        data class Author(val name: String, val link: String?)

        @Serializable
        data class TocItem(val titleJp: String, val titleZh: String?, val chapterId: String?)

        @Serializable
        data class TranslateState(val jp: Long, val baidu: Long, val youdao: Long)
    }

    private suspend fun createMetadataDto(
        metadata: WebNovelMetadataRepository.NovelMetadata,
        username: String?,
    ): NovelMetadataDto {
        val user = username?.let { userRepo.getByUsername(it) }
        val translateState = NovelMetadataDto.TranslateState(
            jp = chapterRepo.count(metadata.providerId, metadata.novelId),
            baidu = chapterRepo.countBaidu(metadata.providerId, metadata.novelId),
            youdao = chapterRepo.countYoudao(metadata.providerId, metadata.novelId),
        )
        return NovelMetadataDto(
            wenkuId = metadata.wenkuId,
            titleJp = metadata.titleJp,
            titleZh = metadata.titleZh,
            authors = metadata.authors.map { NovelMetadataDto.Author(it.name, it.link) },
            introductionJp = metadata.introductionJp,
            introductionZh = metadata.introductionZh,
            glossary = metadata.glossary,
            toc = metadata.toc.map { NovelMetadataDto.TocItem(it.titleJp, it.titleZh ?: "", it.chapterId) },
            visited = metadata.visited,
            syncAt = metadata.syncAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
            favored = user?.favoriteBooks?.any { it.providerId == metadata.providerId && it.novelId == metadata.novelId },
            translateState = translateState,
        )
    }

    suspend fun getMetadata(
        providerId: String,
        novelId: String,
        username: String?,
    ): Result<NovelMetadataDto> {
        val metadata = metadataRepo.fineOneOrFetchRemote(providerId, novelId)
            .getOrElse { return httpInternalServerError(it.message) }
        metadataRepo.increaseVisited(providerId, novelId)
        val metadataDto = createMetadataDto(metadata, username)
        return Result.success(metadataDto)
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
    ): Result<NovelMetadataDto> {
        if (body.title == null &&
            body.introduction == null &&
            body.glossary == null &&
            body.toc.isEmpty()
        ) return httpInternalServerError("修改为空")

        val metadata = metadataRepo.findOne(providerId, novelId)
            ?: return httpNotFound("小说不存在")

        fun createTextChangeOrNull(
            jp: String,
            zhOld: String?,
            zhNew: String?
        ): WebNovelPatchHistoryRepository.NovelPatchHistory.TextChange? {
            return if (zhNew != null && zhNew != zhOld) {
                WebNovelPatchHistoryRepository.NovelPatchHistory.TextChange(jp, zhOld, zhNew)
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
                WebNovelPatchHistoryRepository.NovelPatchHistory.TextChange(
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

        val newMetadata = metadataRepo.updateZh(
            providerId = providerId,
            novelId = novelId,
            titleZh = titleChange?.zhNew.toOptional(),
            introductionZh = introductionChange?.zhNew.toOptional(),
            glossary = glossaryChange.toOptional(),
            tocZh = tocZh,
        )

        val metadataDto = createMetadataDto(newMetadata!!, username)
        return Result.success(metadataDto)
    }

    suspend fun updateWenkuId(
        providerId: String,
        novelId: String,
        wenkuId: String,
        username: String,
    ): Result<NovelMetadataDto> {
        wenkuMetadataRepo.findOne(wenkuId)
            ?: return httpNotFound("文库版不存在")
        val metadata = metadataRepo.updateWenkuId(providerId, novelId, wenkuId)
            ?: return httpNotFound("网页版不存在")
        val metadataDto = createMetadataDto(metadata, username)
        return Result.success(metadataDto)
    }

    suspend fun deleteWenkuId(
        providerId: String,
        novelid: String,
        username: String,
    ): Result<NovelMetadataDto> {
        val metadata = metadataRepo.updateWenkuId(providerId, novelid, null)
            ?: return httpNotFound("网页版不存在")
        val metadataDto = createMetadataDto(metadata, username)
        return Result.success(metadataDto)
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
    )

    suspend fun getChapter(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<NovelChapterDto> {
        val metadata = metadataRepo.fineOneOrFetchRemote(providerId, novelId)
            .getOrElse { return httpInternalServerError(it.message) }

        val toc = metadata.toc.filter { it.chapterId != null }
        val currIndex = toc.indexOfFirst { it.chapterId == chapterId }
        if (currIndex == -1) return httpInternalServerError("episode id not in toc")

        val episode = chapterRepo.get(providerId, novelId, chapterId)
            .getOrElse { return httpInternalServerError(it.message) }

        return Result.success(
            NovelChapterDto(
                titleJp = toc[currIndex].titleJp,
                titleZh = toc[currIndex].titleZh,
                prevId = toc.getOrNull(currIndex - 1)?.chapterId,
                nextId = toc.getOrNull(currIndex + 1)?.chapterId,
                paragraphs = episode.paragraphs,
                baiduParagraphs = episode.baiduParagraphs,
                youdaoParagraphs = episode.youdaoParagraphs,
            )
        )
    }

    // Translator
    @Serializable
    data class TranslateMetadataDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: List<String>,
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val untranslatedChapterIds: List<String>,
        val expiredChapterIds: List<String>,
    )

    suspend fun getMetadataToTranslate(
        providerId: String,
        novelId: String,
        translatorId: String,
        startIndex: Int,
        endIndex: Int,
    ): Result<TranslateMetadataDto> {
        val metadata = metadataRepo.findOne(providerId, novelId)
            ?: return httpNotFound("元数据不存在")

        val title = metadata.titleJp.takeIf { metadata.titleZh == null }
        val introduction = metadata.introductionJp.takeIf { metadata.introductionZh == null }
        val toc = metadata.toc
            .mapNotNull { tocItem -> tocItem.titleJp.takeIf { tocItem.titleZh == null } }
            .distinct()

        val untranslatedChapterIds = mutableListOf<String>()
        val expiredChapterIds = mutableListOf<String>()
        metadata.toc
            .mapNotNull { it.chapterId }
            .safeSubList(startIndex, endIndex)
            .forEach { chapterId ->
                val chapter = chapterRepo.getLocal(providerId, novelId, chapterId)

                if (translatorId == "jp") {
                    if (chapter == null) {
                        untranslatedChapterIds.add(chapterId)
                    }
                } else if (translatorId == "baidu") {
                    if (chapter?.baiduParagraphs == null) {
                        untranslatedChapterIds.add(chapterId)
                    } else if (chapter.baiduGlossaryUuid != metadata.glossaryUuid) {
                        expiredChapterIds.add(chapterId)
                    }
                } else {
                    if (chapter?.youdaoParagraphs == null) {
                        untranslatedChapterIds.add(chapterId)
                    } else if (chapter.youdaoGlossaryUuid != metadata.glossaryUuid) {
                        expiredChapterIds.add(chapterId)
                    }
                }
            }

        return Result.success(
            TranslateMetadataDto(
                title = title,
                introduction = introduction,
                toc = toc,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                untranslatedChapterIds = untranslatedChapterIds,
                expiredChapterIds = expiredChapterIds,
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
        val metadata = metadataRepo.findOne(providerId, novelId)
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

        metadataRepo.updateZh(
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

    suspend fun getChapterToTranslate(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: String,
    ): Result<TranslateChapterDto> {
        if (translatorId != "baidu" && translatorId != "youdao" && translatorId != "jp")
            return httpBadRequest("不支持的版本")

        val chapter = chapterRepo.get(providerId, novelId, chapterId)
            .getOrElse { return httpInternalServerError(it.message) }

        return Result.success(
            TranslateChapterDto(
                glossary = if (translatorId == "baidu") chapter.baiduGlossary else chapter.youdaoGlossary,
                paragraphsJp = chapter.paragraphs,
            )
        )
    }

    @Serializable
    data class TranslateChapterState(val jp: Long, val zh: Long)

    @Serializable
    data class TranslateChapterUpdateBody(
        val glossaryUuid: String? = null,
        val paragraphsZh: List<String>,
    )

    suspend fun updateChapterTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: String,
        body: TranslateChapterUpdateBody,
    ): Result<TranslateChapterState> {
        if (translatorId != "baidu" && translatorId != "youdao")
            return httpBadRequest("不支持的版本")

        val metadata = metadataRepo.findOne(providerId, novelId)
            ?: return httpNotFound("元数据不存在")
        if (body.glossaryUuid != metadata.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.getLocal(providerId, novelId, chapterId)
            ?: return httpNotFound("章节不存在")
        if (chapter.paragraphs.size != body.paragraphsZh.size) {
            return httpBadRequest("翻译文本长度不匹配")
        }

        if (translatorId == "baidu") {
            if (chapter.baiduParagraphs != null) {
                return httpConflict("翻译已经存在")
            }
            chapterRepo.updateBaidu(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        } else {
            if (chapter.youdaoParagraphs != null) {
                return httpConflict("翻译已经存在")
            }
            chapterRepo.updateYoudao(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        }
        metadataRepo.updateChangeAt(providerId, novelId)

        val translateState = TranslateChapterState(
            jp = chapterRepo.count(providerId, novelId),
            zh = when (translatorId) {
                "baidu" -> chapterRepo.countBaidu(providerId, novelId)
                else /* youdao */ -> chapterRepo.countYoudao(providerId, novelId)
            }
        )
        return Result.success(translateState)
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
        translatorId: String,
        body: TranslateChapterUpdatePartlyBody,
    ): Result<TranslateChapterState> {
        if (translatorId != "baidu" && translatorId != "youdao")
            return httpBadRequest("不支持的版本")

        val metadata = metadataRepo.findOne(providerId, novelId)
            ?: return httpNotFound("元数据不存在")
        if (body.glossaryUuid != metadata.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.getLocal(providerId, novelId, chapterId)
            ?: return httpNotFound("章节不存在")

        if (translatorId == "baidu") {
            if (chapter.baiduParagraphs == null) {
                return httpNotFound("翻译不存在")
            }
            chapterRepo.updateBaidu(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        } else {
            if (chapter.youdaoParagraphs == null) {
                return httpNotFound("翻译不存在")
            }
            chapterRepo.updateYoudao(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        }
        metadataRepo.updateChangeAt(providerId, novelId)

        val translateState = TranslateChapterState(
            jp = chapterRepo.count(providerId, novelId),
            zh = when (translatorId) {
                "baidu" -> chapterRepo.countBaidu(providerId, novelId)
                else /* youdao */ -> chapterRepo.countYoudao(providerId, novelId)
            }
        )
        return Result.success(translateState)
    }

    suspend fun updateFile(
        providerId: String,
        novelId: String,
        lang: NovelFileLang,
        type: NovelFileType,
    ): Result<String> {
        val fileName = "${providerId}.${novelId}.${lang.value}.${type.value}"

        val metadata = metadataRepo.findOne(providerId, novelId)
            ?: return httpNotFound("小说不存在")

        val shouldMake = fileRepo.getCreationTime(fileName)?.let { fileCreateAt ->
            val updateAt = metadata.changeAt.atZone(ZoneId.systemDefault()).toInstant()
            updateAt > fileCreateAt
        } ?: true

        if (shouldMake) {
            val chapters = metadata.toc
                .mapNotNull { it.chapterId }
                .mapNotNull { chapterId ->
                    chapterRepo
                        .getLocal(providerId, novelId, chapterId)
                        ?.let { chapterId to it }
                }
                .toMap()
            fileRepo.makeFile(
                fileName = fileName,
                lang = lang,
                type = type,
                metadata = metadata,
                episodes = chapters,
            )
        }
        return Result.success(fileName)
    }
}
