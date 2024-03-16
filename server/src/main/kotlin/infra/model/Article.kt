package infra.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.Id

@Serializable
enum class ArticleCategory {
    @SerialName("Guide")
    Guide,

    @SerialName("General")
    General,

    @SerialName("Support")
    Support,
}

@Serializable
data class ArticleOutline(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val category: ArticleCategory,
    val locked: Boolean,
    val pinned: Boolean,
    val numViews: Int,
    val numComments: Int,
    val user: UserOutline,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
)

@Serializable
data class Article(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val content: String,
    val category: ArticleCategory,
    val locked: Boolean,
    val pinned: Boolean,
    val numViews: Int,
    val numComments: Int,
    @Contextual val user: UserOutline,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
)

@Serializable
data class ArticleModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val content: String,
    val category: ArticleCategory,
    val locked: Boolean,
    val pinned: Boolean,
    val numViews: Int,
    val numComments: Int,
    @Contextual val user: Id<User>,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
    @Contextual val changeAt: Instant,
)
