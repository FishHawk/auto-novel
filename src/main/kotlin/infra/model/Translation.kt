package infra.model

enum class TranslatorId { Baidu, Youdao }

data class TranslationState(
    val total: Long,
    val baidu: Long,
    val youdao: Long,
)