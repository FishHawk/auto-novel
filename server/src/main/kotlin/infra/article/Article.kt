package infra.article

import infra.user.UserOutline
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

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
data class ArticleListItem(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val category: ArticleCategory,
    val locked: Boolean,
    val pinned: Boolean,
    val hidden: Boolean,
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
    val hidden: Boolean,
    val numViews: Int,
    val numComments: Int,
    @Contextual val user: UserOutline,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
)

// MongoDB
@Serializable
data class ArticleDbModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val content: String,
    val category: ArticleCategory,
    val locked: Boolean,
    val pinned: Boolean,
    val hidden: Boolean = false,
    val numViews: Int,
    val numComments: Int,
    @Contextual val user: ObjectId,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
    @Contextual val changeAt: Instant,
)
