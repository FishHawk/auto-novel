package infra.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NovelFileLangV2 {
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
    MIX_YOUDAO("mix-youdao"),

    @SerialName("zh-gpt")
    ZH_GPT("zh-gpt"),

    @SerialName("mix-gpt")
    MIX_GPT("mix-gpt"),

    @SerialName("mix-all")
    MIX_ALL("mix-all")
}

@Serializable
enum class NovelFileType(val value: String) {
    @SerialName("epub")
    EPUB("epub"),

    @SerialName("txt")
    TXT("txt")
}
