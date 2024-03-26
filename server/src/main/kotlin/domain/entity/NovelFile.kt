package domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NovelFileMode {
    @SerialName("jp")
    Jp,

    @SerialName("zh")
    Zh,

    @SerialName("jp-zh")
    JpZh,

    @SerialName("zh-jp")
    ZhJp,
}

@Serializable
enum class NovelFileTranslationsMode {
    @SerialName("parallel")
    Parallel,

    @SerialName("priority")
    Priority,
}

@Serializable
enum class NovelFileType(val value: String) {
    @SerialName("epub")
    EPUB("epub"),

    @SerialName("txt")
    TXT("txt")
}
