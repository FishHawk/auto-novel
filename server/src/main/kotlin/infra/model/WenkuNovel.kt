package infra.model

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
    val titleZhAlias: List<String>,
    val cover: String,
    val coverSmall: String,
    val authors: List<String>,
    val artists: List<String>,
    val keywords: List<String>,
    val introduction: String,
    val glossaryUuid: String? = null,
    val glossary: Map<String, String> = emptyMap(),
    val visited: Long,
) {
    companion object {
        fun byId(id: String): Bson = WenkuNovelMetadata::id eq ObjectId(id)
    }
}

data class WenkuNovelVolumeList(
    val jp: List<WenkuNovelVolumeJp>,
    val zh: List<String>,
)

@Serializable
data class WenkuNovelVolumeJp(
    val volumeId: String,
    val total: Long,
    val baidu: Long,
    val youdao: Long,
    val gpt: Long,
)