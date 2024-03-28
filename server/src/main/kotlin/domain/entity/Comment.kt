package domain.entity

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.Id

@Serializable
data class Comment(
    @Contextual @SerialName("_id") val id: ObjectId,
    val site: String,
    val content: String,
    val hidden: Boolean = false,
    val numReplies: Int,
    @Contextual val parent: Id<Comment>?,
    @Contextual val user: Id<User>,
    @Contextual val createAt: Instant,
)

@Serializable
data class CommentWithUserReadModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    val site: String,
    val content: String,
    val hidden: Boolean,
    val numReplies: Int,
    @Contextual val user: UserOutline,
    @Contextual val createAt: Instant,
)
