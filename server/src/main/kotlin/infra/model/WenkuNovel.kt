package infra.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.eq

@Serializable
data class WenkuNovelMetadataOutline(
    val id: String,
    val title: String,
    val titleZh: String,
    val cover: String,
)

@Serializable
data class WenkuNovelMetadata(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val titleZh: String,
    val cover: String,
    val authors: List<String>,
    val artists: List<String>,
    val keywords: List<String>,
    val r18: Boolean = false,
    val introduction: String,
    val volumes: List<WenkuNovelVolume> = emptyList(),
    val glossaryUuid: String? = null,
    val glossary: Map<String, String> = emptyMap(),
    val visited: Long,
    @Contextual val updateAt: Instant = Clock.System.now(),
) {
    companion object {
        fun byId(id: String): Bson = WenkuNovelMetadata::id eq ObjectId(id)
    }
}

@Serializable
data class WenkuNovelVolume(
    val asin: String,
    val title: String,
    val titleZh: String? = null,
    val cover: String,
)

data class WenkuNovelVolumeList(
    val jp: List<WenkuNovelVolumeJp>,
    val zh: List<String>,
)

@Serializable
data class WenkuNovelVolumeJp(
    val volumeId: String,
    val total: Int,
    val baidu: Int,
    val youdao: Int,
    val gpt: Int,
    val sakura: Int,
)

@Serializable
data class WenkuChapterGlossary(
    val uuid: String?,
    val glossary: Map<String, String>,
    val sakuraVersion: String?,
)
