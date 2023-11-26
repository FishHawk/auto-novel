package api.model

import infra.model.WenkuNovelMetadataOutline
import kotlinx.serialization.Serializable

@Serializable
data class WenkuNovelOutlineDto(
    val id: String,
    val title: String,
    val titleZh: String,
    val cover: String,
)

fun WenkuNovelMetadataOutline.asDto() =
    WenkuNovelOutlineDto(
        id = id,
        title = title,
        titleZh = titleZh,
        cover = cover,
    )

