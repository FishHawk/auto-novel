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
    val type: WebNovelType?,
    val attentions: List<WebNovelAttention>,
    val keywords: List<String>,
    val extra: String?,
    val total: Long,
    val jp: Long,
    val baidu: Long,
    val youdao: Long,
    val gpt: Long,
) {
    companion object {
        fun fromDomain(domain: WebNovelMetadataOutline) =
            WebNovelOutlineDto(
                providerId = domain.providerId,
                novelId = domain.novelId,
                titleJp = domain.titleJp,
                titleZh = domain.titleZh,
                type = domain.type,
                attentions = domain.attentions,
                keywords = domain.keywords,
                extra = domain.extra,
                total = domain.total,
                jp = domain.jp,
                baidu = domain.baidu,
                youdao = domain.youdao,
                gpt = domain.gpt,
            )
    }
}

@Serializable
data class WebNovelDto(
    val wenkuId: String?,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<WebNovelAuthorDto>,
    val type: WebNovelType?,
    val attentions: List<WebNovelAttention>,
    val keywords: List<String>,
    val introductionJp: String,
    val introductionZh: String?,
    val glossary: Map<String, String>,
    val toc: List<WebNovelTocItemDto>,
    val visited: Long,
    val syncAt: Long,
    val favored: Boolean?,
    val lastReadChapterId: String?,
    val jp: Long,
    val baidu: Long,
    val youdao: Long,
    val gpt: Long,
) {
    companion object {
        fun fromDomain(
            novel: WebNovelMetadata,
            favored: Boolean?,
            lastReadChapterId: String?,
        ) = WebNovelDto(
            wenkuId = novel.wenkuId,
            titleJp = novel.titleJp,
            titleZh = novel.titleZh,
            authors = novel.authors.map { WebNovelAuthorDto.fromDomain(it) },
            type = novel.type,
            attentions = novel.attentions,
            keywords = novel.keywords,
            introductionJp = novel.introductionJp,
            introductionZh = novel.introductionZh,
            glossary = novel.glossary,
            toc = novel.toc.map { WebNovelTocItemDto.fromDomain(it) },
            visited = novel.visited,
            syncAt = novel.syncAt.atZone(ZoneId.systemDefault()).toEpochSecond(),
            favored = favored,
            lastReadChapterId = lastReadChapterId,
            jp = novel.jp,
            baidu = novel.baidu,
            youdao = novel.youdao,
            gpt = novel.gpt,
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
    val createAt: Long?,
) {
    companion object {
        fun fromDomain(domain: WebNovelTocItem) =
            WebNovelTocItemDto(
                titleJp = domain.titleJp,
                titleZh = domain.titleZh,
                chapterId = domain.chapterId,
                createAt = domain.createAt?.epochSeconds,
            )
    }
}


@Serializable
data class WebNovelChapterDto(
    val titleJp: String,
    val titleZh: String? = null,
    val prevId: String? = null,
    val nextId: String? = null,
    val paragraphs: List<String>,
    val baiduParagraphs: List<String>? = null,
    val youdaoParagraphs: List<String>? = null,
    val gptParagraphs: List<String>? = null,
)

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