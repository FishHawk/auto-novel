package domain.entity

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SakuraWebIncorrectCase(
    val providerId: String,
    val novelId: String,
    val chapterId: String,
    val uploader: String,
    val jp: String,
    val zh: String,
    val contextJp: List<String>,
    val contextZh: List<String>,
    val createAt: Instant,
)
