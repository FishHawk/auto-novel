package api.dto

import infra.model.*
import kotlinx.serialization.Serializable
import java.time.ZoneId

@Serializable
data class WenkuNovelOutlineDto(
    val id: String,
    val title: String,
    val titleZh: String,
    val cover: String,
) {
    companion object {
        fun fromDomain(domain: WenkuNovelMetadataOutline) =
            WenkuNovelOutlineDto(
                id = domain.id,
                title = domain.title,
                titleZh = domain.titleZh,
                cover = domain.cover,
            )

        fun fromDomain(domain: WenkuNovelMetadata): WenkuNovelOutlineDto =
            WenkuNovelOutlineDto(
                id = domain.id.toHexString(),
                title = domain.title,
                titleZh = domain.titleZh,
                cover = domain.cover,
            )
    }
}

@Serializable
data class WenkuNovelDto(
    val title: String,
    val titleZh: String,
    val titleZhAlias: List<String>,
    val cover: String,
    val coverSmall: String,
    val authors: List<String>,
    val artists: List<String>,
    val keywords: List<String>,
    val introduction: String,
    val visited: Long,
    val favored: Boolean?,
    val volumeZh: List<String>,
    val volumeJp: List<VolumeJpDto>,
)

@Serializable
data class VolumeJpDto(
    val volumeId: String,
    val total: Long,
    val baidu: Long,
    val youdao: Long,
) {
    companion object {
        fun fromDomain(
            volumeId: String,
            state: TranslationState,
        ) =
            VolumeJpDto(
                volumeId = volumeId,
                total = state.total,
                baidu = state.baidu,
                youdao = state.youdao,
            )
    }
}

