package api

import data.web.WebChapterRepository
import data.web.WebNovelMetadataRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.None
import util.toOptional

@Resource("/update")
private class Update {
    @Resource("/metadata/{providerId}/{novelId}")
    data class Metadata(
        val parent: Update = Update(),
        val providerId: String,
        val novelId: String,
        val version: String = "jp",
        val startIndex: Int = 0,
        val endIndex: Int = 65536,
    )

    @Resource("/chapter/{providerId}/{novelId}/{chapterId}")
    data class Chapter(
        val parent: Update = Update(),
        val providerId: String,
        val novelId: String,
        val chapterId: String,
        val version: String = "jp",
    )
}

fun Route.routeUpdate() {
    val service by inject<UpdateService>()

    get<Update.Metadata> { loc ->
        val result = service.getMetadataToTranslate(
            providerId = loc.providerId,
            novelId = loc.novelId,
            version = loc.version,
            startIndex = loc.startIndex,
            endIndex = loc.endIndex,
        )
        call.respondResult(result)
    }

    post<Update.Metadata> { loc ->
        val metadataTranslated = call.receive<UpdateService.MetadataUpdateBody>()
        val result = service.updateMetadata(
            providerId = loc.providerId,
            novelId = loc.novelId,
            body = metadataTranslated,
        )
        call.respondResult(result)
    }

    get<Update.Chapter> { loc ->
        val result = service.getChapterToTranslate(
            providerId = loc.providerId,
            novelId = loc.novelId,
            chapterId = loc.chapterId,
            version = loc.version,
        )
        call.respondResult(result)
    }

    post<Update.Chapter> { loc ->
        val body = call.receive<UpdateService.ChapterUpdateBody>()
        val result = service.updateChapter(
            providerId = loc.providerId,
            novelId = loc.novelId,
            chapterId = loc.chapterId,
            version = loc.version,
            body = body,
        )
        call.respondResult(result)
    }

    put<Update.Chapter> { loc ->
        val body = call.receive<UpdateService.ChapterUpdatePartlyBody>()
        val result = service.updateChapterPartly(
            providerId = loc.providerId,
            novelId = loc.novelId,
            chapterId = loc.chapterId,
            version = loc.version,
            body = body,
        )
        call.respondResult(result)
    }
}

class UpdateService(
    private val metadataRepo: WebNovelMetadataRepository,
    private val chapterRepo: WebChapterRepository,
) {
    @Serializable
    data class MetadataToTranslateDto(
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
        version: String,
        startIndex: Int,
        endIndex: Int,
    ): Result<MetadataToTranslateDto> {
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

                if (version == "jp") {
                    if (chapter == null) {
                        untranslatedChapterIds.add(chapterId)
                    }
                } else if (version == "baidu") {
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
            MetadataToTranslateDto(
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
    data class MetadataUpdateBody(
        val title: String? = null,
        val introduction: String? = null,
        val toc: Map<String, String>,
    )

    suspend fun updateMetadata(
        providerId: String,
        novelId: String,
        body: MetadataUpdateBody,
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
    data class ChapterToTranslateDto(
        val glossary: Map<String, String>,
        val paragraphsJp: List<String>,
    )

    suspend fun getChapterToTranslate(
        providerId: String,
        novelId: String,
        chapterId: String,
        version: String,
    ): Result<ChapterToTranslateDto> {
        if (version != "baidu" && version != "youdao" && version != "jp")
            return httpBadRequest("不支持的版本")

        val chapter = chapterRepo.get(providerId, novelId, chapterId)
            .getOrElse { return httpInternalServerError(it.message) }

        return Result.success(
            ChapterToTranslateDto(
                glossary = if (version == "baidu") chapter.baiduGlossary else chapter.youdaoGlossary,
                paragraphsJp = chapter.paragraphs,
            )
        )
    }

    @Serializable
    data class ChapterUpdateBody(
        val glossaryUuid: String? = null,
        val paragraphsZh: List<String>,
    )

    suspend fun updateChapter(
        providerId: String,
        novelId: String,
        chapterId: String,
        version: String,
        body: ChapterUpdateBody,
    ): Result<Unit> {
        if (version != "baidu" && version != "youdao")
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

        if (version == "baidu") {
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
        return Result.success(Unit)
    }

    @Serializable
    data class ChapterUpdatePartlyBody(
        val glossaryUuid: String? = null,
        val paragraphsZh: Map<Int, String>,
    )

    suspend fun updateChapterPartly(
        providerId: String,
        novelId: String,
        chapterId: String,
        version: String,
        body: ChapterUpdatePartlyBody,
    ): Result<Unit> {
        if (version != "baidu" && version != "youdao")
            return httpBadRequest("不支持的版本")

        val metadata = metadataRepo.findOne(providerId, novelId)
            ?: return httpNotFound("元数据不存在")
        if (body.glossaryUuid != metadata.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.getLocal(providerId, novelId, chapterId)
            ?: return httpNotFound("章节不存在")

        if (version == "baidu") {
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
        return Result.success(Unit)
    }
}

fun <T> List<T>.safeSubList(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex >= size || toIndex <= 0 || fromIndex >= toIndex) {
        return emptyList()
    }
    return subList(
        fromIndex.coerceAtLeast(0),
        toIndex.coerceAtMost(size)
    )
}
