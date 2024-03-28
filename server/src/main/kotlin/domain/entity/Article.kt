package domain.entity

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
data class Article(
    @Contextual @SerialName("_id") val id: ObjectId,
    val title: String,
    val content: String,
    val category: ArticleCategory,
    val locked: Boolean,
    val pinned: Boolean,
    val hidden: Boolean = false,
    val numViews: Int,
    val numComments: Int,
    @Contextual val user: Id<User>,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
    @Contextual val changeAt: Instant,
)

@Serializable
data class ArticleSimplifiedWithUserReadModel(
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
data class ArticleWithUserReadModel(
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
