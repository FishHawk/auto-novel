package infra.comment

import infra.user.UserOutline
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Comment(
    val id: String,
    val site: String,
    val content: String,
    val hidden: Boolean,
    val numReplies: Int,
    @Contextual val user: UserOutline,
    @Contextual val createAt: Instant,
)

// MongoDB
@Serializable
data class CommentDbModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    val site: String,
    val content: String,
    val hidden: Boolean = false,
    val numReplies: Int,
    @Contextual val parent: ObjectId?,
    @Contextual val user: ObjectId,
    @Contextual val createAt: Instant,
)
