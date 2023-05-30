package infra.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NovelFileLang(val value: String) {
    @SerialName("jp")
    JP("jp"),

    @SerialName("zh-baidu")
    ZH_BAIDU("zh-baidu"),

    @SerialName("mix-baidu")
    MIX_BAIDU("mix-baidu"),

    @SerialName("zh-youdao")
    ZH_YOUDAO("zh-youdao"),

    @SerialName("mix-youdao")
    MIX_YOUDAO("mix-youdao")
}

@Serializable
enum class NovelFileType(val value: String) {
    @SerialName("epub")
    EPUB("epub"),

    @SerialName("txt")
    TXT("txt")
}
