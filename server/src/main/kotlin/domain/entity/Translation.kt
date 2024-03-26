package domain.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TranslatorId {
    @SerialName("baidu")
    Baidu,

    @SerialName("youdao")
    Youdao,

    @SerialName("gpt")
    Gpt,

    @SerialName("sakura")
    Sakura,
}

data class Glossary(
    val id: String,
    val map: Map<String, String>,
)