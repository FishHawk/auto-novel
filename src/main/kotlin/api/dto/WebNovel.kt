package api.dto

import infra.model.*
import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
data class WebNovelOutlineDto(
    val providerId: String,
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val extra: String?,
    val total: Long?,
    val count: Long?,
    val countBaidu: Long?,
    val countYoudao: Long?,
) {
    companion object {
        fun fromDomain(
            outline: WebNovelMetadataOutline,
            state: TranslationState?,
        ) = WebNovelOutlineDto(
            providerId = outline.providerId,
            novelId = outline.novelId,
            titleJp = outline.titleJp,
            titleZh = outline.titleZh,
            extra = outline.extra,
            total = state?.total,
            count = state?.jp,
            countBaidu = state?.baidu,
            countYoudao = state?.youdao,
        )

        fun fromDomain(
            outline: WebNovelMetadata,
            state: TranslationState?,
        ) = WebNovelOutlineDto(
            providerId = outline.providerId,
            novelId = outline.novelId,
            titleJp = outline.titleJp,
            titleZh = outline.titleZh,
            extra = null,
            total = state?.total,
            count = state?.jp,
            countBaidu = state?.baidu,
            countYoudao = state?.youdao,
        )
    }
}

@Serializable
data class WebNovelDto(
    val wenkuId: String?,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<WebNovelAuthorDto>,
    val introductionJp: String,
    val introductionZh: String?,
    val glossary: Map<String, String>,
    val toc: List<WebNovelTocItemDto>,
    val visited: Long,
    val syncAt: Long,
    val favored: Boolean?,
    val translateState: TranslationStateDto,
) {
    companion object {
        fun fromDomain(
            novel: WebNovelMetadata,
            state: TranslationState,
            favored: Boolean?,
        ) = WebNovelDto(
            wenkuId = novel.wenkuId,
            titleJp = novel.titleJp,
            titleZh = novel.titleZh,
            authors = novel.authors.map { WebNovelAuthorDto.fromDomain(it) },
            introductionJp = novel.introductionJp,
            introductionZh = novel.introductionZh,
            glossary = novel.glossary,
            toc = novel.toc.map { WebNovelTocItemDto.fromDomain(it) },
            visited = novel.visited,
            syncAt = novel.syncAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
            favored = favored,
            translateState = TranslationStateDto.fromDomain(state)
        )
    }
}

@Serializable
data class WebNovelAuthorDto(
    val name: String,
    val link: String?,
) {
    companion object {
        fun fromDomain(domain: WebNovelAuthor) =
            WebNovelAuthorDto(
                name = domain.name,
                link = domain.link,
            )
    }
}

@Serializable
data class WebNovelTocItemDto(
    val titleJp: String,
    val titleZh: String?,
    val chapterId: String?,
) {
    companion object {
        fun fromDomain(domain: WebNovelTocItem) =
            WebNovelTocItemDto(
                titleJp = domain.titleJp,
                titleZh = domain.titleZh,
                chapterId = domain.chapterId,
            )
    }
}

@Serializable
data class TranslationStateDto(
    val jp: Long,
    val baidu: Long,
    val youdao: Long,
) {
    companion object {
        fun fromDomain(domain: TranslationState) =
            TranslationStateDto(
                jp = domain.jp,
                baidu = domain.baidu,
                youdao = domain.youdao,
            )
    }
}

@Serializable
data class WebNovelTocMergeHistoryOutlineDto(
    val id: String,
    val providerId: String,
    val novelId: String,
    val reason: String,
) {
    companion object {
        fun fromDomain(domain: WebNovelTocMergeHistory) =
            WebNovelTocMergeHistoryOutlineDto(
                id = domain.id.toHexString(),
                providerId = domain.providerId,
                novelId = domain.novelId,
                reason = domain.reason,
            )
    }
}

@Serializable
data class WebNovelTocMergeHistoryDto(
    val id: String,
    val providerId: String,
    val novelId: String,
    val tocOld: List<WebNovelTocItemDto>,
    val tocNew: List<WebNovelTocItemDto>,
    val reason: String,
) {
    companion object {
        fun fromDomain(domain: WebNovelTocMergeHistory) =
            WebNovelTocMergeHistoryDto(
                id = domain.id.toHexString(),
                providerId = domain.providerId,
                novelId = domain.novelId,
                tocOld = domain.tocOld.map { WebNovelTocItemDto.fromDomain(it) },
                tocNew = domain.tocNew.map { WebNovelTocItemDto.fromDomain(it) },
                reason = domain.reason,
            )
    }
}

@Serializable
data class WebNovelPatchHistoryOutlineDto(
    val providerId: String,
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
) {
    companion object {
        fun fromDomain(domain: WebNovelPatchHistoryOutline) =
            WebNovelPatchHistoryOutlineDto(
                providerId = domain.providerId,
                novelId = domain.novelId,
                titleJp = domain.titleJp,
                titleZh = domain.titleZh,
            )
    }
}

@Serializable
data class WebNovelPatchHistoryDto(
    val providerId: String,
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val patches: List<WebNovelPatchHistory.Patch>,
) {
    companion object {
        fun fromDomain(domain: WebNovelPatchHistory) =
            WebNovelPatchHistoryDto(
                providerId = domain.providerId,
                novelId = domain.novelId,
                titleJp = domain.titleJp,
                titleZh = domain.titleZh,
                patches = domain.patches,
            )
    }
}