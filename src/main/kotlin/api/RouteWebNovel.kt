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
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
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
        @Resource("/state")
        data class State(val parent: Id)

        @Resource("/wenku")
        data class Wenku(val parent: Id)

        @Resource("/chapter/{chapterId}")
        data class Chapter(val parent: Id, val chapterId: String)
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

    get<WebNovel.Id.State> { loc ->
        val result = service.getState(loc.parent.providerId, loc.parent.novelId)
        call.respondResult(result)
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
}

class WebNovelService(
    private val metadataRepo: WebNovelMetadataRepository,
    private val episodeRepo: WebChapterRepository,
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
                count = episodeRepo.count(it.providerId, it.novelId),
                countBaidu = episodeRepo.countBaidu(it.providerId, it.novelId),
                countYoudao = episodeRepo.countYoudao(it.providerId, it.novelId),
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
    data class NovelStateDto(
        val total: Int,
        val count: Long,
        val countBaidu: Long,
        val countYoudao: Long,
    )

    suspend fun getState(
        providerId: String,
        novelId: String,
    ): Result<NovelStateDto> {
        val metadata = metadataRepo.fineOneOrFetchRemote(providerId, novelId)
            .getOrElse { return httpInternalServerError(it.message) }
        return Result.success(
            NovelStateDto(
                total = metadata.toc.count { it.chapterId != null },
                count = episodeRepo.count(metadata.providerId, metadata.novelId),
                countBaidu = episodeRepo.countBaidu(metadata.providerId, metadata.novelId),
                countYoudao = episodeRepo.countYoudao(metadata.providerId, metadata.novelId),
            )
        )
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
    ) {
        @Serializable
        data class Author(val name: String, val link: String?)

        @Serializable
        data class TocItem(val titleJp: String, val titleZh: String?, val chapterId: String?)

    }

    private suspend fun createMetadataDto(
        metadata: WebNovelMetadataRepository.NovelMetadata,
        username: String?,
    ): NovelMetadataDto {
        val user = username?.let { userRepo.getByUsername(it) }
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

        val episode = episodeRepo.get(providerId, novelId, chapterId)
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
}
