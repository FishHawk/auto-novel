package api.dto

import infra.model.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

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
    val volumeJp: List<WenkuNovelVolumeJp>,
)

@Serializable
data class WenkuNovelUploadHistoryDto(
    val id: String,
    val novelId: String,
    val volumeId: String,
    val uploader: String,
    val createAt: Long,
) {
    companion object {
        fun fromDomain(domain: WenkuNovelUploadHistory) =
            WenkuNovelUploadHistoryDto(
                id = domain.id.toHexString(),
                novelId = domain.novelId,
                volumeId = domain.volumeId,
                uploader = domain.uploader,
                createAt = domain.createAt.epochSeconds,
            )
    }
}