package api

import data.web.BookEpisodeRepository
import data.web.BookMetadataRepository
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
@Resource("/update")
private class Update {
    @Serializable
    @Resource("/metadata/{providerId}/{bookId}")
    data class Metadata(
        val parent: Update = Update(),
        val providerId: String,
        val bookId: String,
        val version: String,
        val startIndex: Int = 0,
        val endIndex: Int = 65536,
    )

    @Serializable
    @Resource("/episode/{providerId}/{bookId}/{episodeId}")
    data class Episode(
        val parent: Update = Update(),
        val providerId: String,
        val bookId: String,
        val episodeId: String,
    )
}

fun Route.routeUpdate() {
    val service by inject<UpdateService>()

    get<Update.Metadata> { loc ->
        val result = service.getMetadataToTranslate(
            providerId = loc.providerId,
            bookId = loc.bookId,
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
            bookId = loc.bookId,
            body = metadataTranslated,
        )
        call.respondResult(result)
    }

    get<Update.Episode> { loc ->
        val result = service.getEpisodeToTranslate(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
        )
        call.respondResult(result)
    }

    post<Update.Episode> { loc ->
        val body = call.receive<UpdateService.EpisodeUpdateBody>()
        val result = service.updateEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
            body = body,
        )
        call.respondResult(result)
    }

    put<Update.Episode> { loc ->
        val body = call.receive<UpdateService.EpisodeUpdatePartlyBody>()
        val result = service.updateEpisodePartly(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
            body = body,
        )
        call.respondResult(result)
    }
}

class UpdateService(
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
) {
    @Serializable
    data class MetadataToTranslateDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: List<String>,
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val untranslatedEpisodeIds: List<String>,
        val expiredEpisodeIds: List<String>,
    )

    suspend fun getMetadataToTranslate(
        providerId: String,
        bookId: String,
        version: String,
        startIndex: Int,
        endIndex: Int,
    ): Result<MetadataToTranslateDto> {
        if (version != "baidu" && version != "youdao")
            return httpBadRequest("不支持的版本")

        val metadata = bookMetadataRepository.getLocal(providerId, bookId)
            ?: return httpNotFound("元数据不存在")

        val title = metadata.titleJp.takeIf { metadata.titleZh == null }

        val introduction = metadata.introductionJp.takeIf { metadata.introductionZh == null }

        val toc = metadata.toc
            .mapNotNull { tocItem -> tocItem.titleJp.takeIf { tocItem.titleZh == null } }
            .distinct()

        val untranslatedEpisodeIds = mutableListOf<String>()
        val expiredEpisodeIds = mutableListOf<String>()
        metadata.toc
            .mapNotNull { it.episodeId }
            .safeSubList(startIndex, endIndex)
            .forEach { episodeId ->
                val episode = bookEpisodeRepository.getLocal(providerId, bookId, episodeId)

                if (version == "baidu") {
                    if (episode?.baiduParagraphs == null) {
                        untranslatedEpisodeIds.add(episodeId)
                    } else if (episode.baiduGlossaryUuid != metadata.glossaryUuid) {
                        expiredEpisodeIds.add(episodeId)
                    }
                } else {
                    if (episode?.youdaoParagraphs == null) {
                        untranslatedEpisodeIds.add(episodeId)
                    } else if (episode.youdaoGlossaryUuid != metadata.glossaryUuid) {
                        expiredEpisodeIds.add(episodeId)
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
                untranslatedEpisodeIds = untranslatedEpisodeIds,
                expiredEpisodeIds = expiredEpisodeIds,
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
        bookId: String,
        body: MetadataUpdateBody,
    ): Result<Unit> {
        val metadata = bookMetadataRepository.getLocal(providerId, bookId)
            ?: return Result.success(Unit)

        val titleZh = body.title.takeIf {
            metadata.titleZh == null
        }
        val introductionZh = body.introduction.takeIf {
            metadata.introductionZh == null
        }
        val tocZh = metadata.toc.mapIndexedNotNull { index, item ->
            if (item.titleZh == null) {
                body.toc[item.titleJp]?.let { index to it }
            } else {
                null
            }
        }.toMap()

        if (titleZh == null &&
            introductionZh == null &&
            tocZh.isEmpty()
        ) {
            return Result.success(Unit)
        }

        bookMetadataRepository.updateZh(
            providerId = providerId,
            bookId = bookId,
            titleZh = body.title,
            introductionZh = body.introduction,
            glossary = null,
            tocZh = tocZh,
        )
        return Result.success(Unit)
    }

    @Serializable
    data class EpisodeToTranslateDto(
        val glossary: Map<String, String>,
        val paragraphsJp: List<String>,
    )

    suspend fun getEpisodeToTranslate(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): Result<EpisodeToTranslateDto> {
        val episode = bookEpisodeRepository.get(providerId, bookId, episodeId)
            .getOrElse { return httpInternalServerError(it.message) }

        return Result.success(
            EpisodeToTranslateDto(
                glossary = episode.baiduGlossary,
                paragraphsJp = episode.paragraphs,
            )
        )
    }

    @Serializable
    data class EpisodeUpdateBody(
        val version: String,
        val glossaryUuid: String? = null,
        val paragraphsZh: List<String>,
    )

    suspend fun updateEpisode(
        providerId: String,
        bookId: String,
        episodeId: String,
        body: EpisodeUpdateBody,
    ): Result<Unit> {
        if (body.version != "baidu" && body.version != "youdao")
            return httpBadRequest("不支持的版本")

        val metadata = bookMetadataRepository.getLocal(providerId, bookId)
            ?: return httpNotFound("元数据不存在")
        if (body.glossaryUuid != metadata.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val episode = bookEpisodeRepository.getLocal(providerId, bookId, episodeId)
            ?: return httpNotFound("章节不存在")
        if (episode.paragraphs.size != body.paragraphsZh.size) {
            return httpBadRequest("翻译文本长度不匹配")
        }

        if (body.version == "baidu") {
            if (episode.baiduParagraphs != null) {
                return httpConflict("翻译已经存在")
            }
            bookEpisodeRepository.updateBaidu(
                providerId = providerId,
                bookId = bookId,
                episodeId = episodeId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        } else {
            if (episode.youdaoParagraphs != null) {
                return httpConflict("翻译已经存在")
            }
            bookEpisodeRepository.updateYoudao(
                providerId = providerId,
                bookId = bookId,
                episodeId = episodeId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        }
        return Result.success(Unit)
    }

    @Serializable
    data class EpisodeUpdatePartlyBody(
        val version: String,
        val glossaryUuid: String? = null,
        val paragraphsZh: Map<Int, String>,
    )

    suspend fun updateEpisodePartly(
        providerId: String,
        bookId: String,
        episodeId: String,
        body: EpisodeUpdatePartlyBody,
    ): Result<Unit> {
        if (body.version != "baidu" && body.version != "youdao")
            return httpBadRequest("不支持的版本")

        val metadata = bookMetadataRepository.getLocal(providerId, bookId)
            ?: return httpNotFound("元数据不存在")
        if (body.glossaryUuid != metadata.glossaryUuid) {
            return httpBadRequest("术语表uuid失效")
        }

        val episode = bookEpisodeRepository.getLocal(providerId, bookId, episodeId)
            ?: return httpNotFound("章节不存在")

        if (body.version == "baidu") {
            if (episode.baiduParagraphs != null) {
                return httpConflict("翻译已经存在")
            }
            bookEpisodeRepository.updateBaidu(
                providerId = providerId,
                bookId = bookId,
                episodeId = episodeId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        } else {
            if (episode.youdaoParagraphs != null) {
                return httpConflict("翻译已经存在")
            }
            bookEpisodeRepository.updateYoudao(
                providerId = providerId,
                bookId = bookId,
                episodeId = episodeId,
                glossaryUuid = metadata.glossaryUuid,
                glossary = metadata.glossary,
                paragraphsZh = body.paragraphsZh,
            )
        }
        return Result.success(Unit)
    }
}

private fun <T> List<T>.safeSubList(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex >= size || toIndex <= 0 || fromIndex >= toIndex) {
        return emptyList()
    }
    return subList(
        fromIndex.coerceAtLeast(0),
        toIndex.coerceAtMost(size)
    )
}
