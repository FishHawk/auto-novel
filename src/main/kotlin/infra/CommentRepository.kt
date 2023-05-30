package infra

import com.mongodb.client.model.CountOptions
import infra.model.Page
import infra.model.WenkuNovelMetadata
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.*

@Serializable
data class Comment(
    val id: String,
    val createAt: Int,
    val parentId: String?,
    val username: String,
    val receiver: String?,
    val upvote: Int,
    val downvote: Int,
    val viewerVote: Boolean?,
    val content: String,
)

class CommentRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun list(
        postId: String,
        parentId: String?,
        viewer: String?,
        page: Int,
        pageSize: Int,
        reverse: Boolean = false,
    ): Page<Comment> {
        val total = mongo
            .commentCollection
            .countDocuments(
                and(
                    CommentModel::postId eq postId,
                    CommentModel::parentId eq parentId?.let { ObjectId(it) },
                )
            )
        val items = mongo
            .commentCollection
            .find(
                CommentModel::postId eq postId,
                CommentModel::parentId eq parentId?.let { ObjectId(it) },
            )
            .let { if (reverse) it.descendingSort(CommentModel::id) else it }
            .skip(page * pageSize)
            .limit(pageSize)
            .toList()
            .map {
                Comment(
                    id = it.id.toHexString(),
                    createAt = it.id.timestamp,
                    parentId = it.parentId?.toHexString(),
                    username = it.username,
                    receiver = it.receiver,
                    upvote = it.upvoteUsers.size,
                    downvote = it.downvoteUsers.size,
                    viewerVote = viewer?.let { viewer ->
                        if (it.upvoteUsers.contains(viewer)) {
                            true
                        } else if (it.downvoteUsers.contains(viewer)) {
                            false
                        } else {
                            null
                        }
                    },
                    content = it.content,
                )
            }
        return Page(items = items, total = total)
    }

    suspend fun exist(
        id: String?,
    ): Boolean {
        return mongo
            .commentCollection
            .countDocuments(
                CommentModel::id eq ObjectId(id),
                CountOptions().limit(1),
            ) != 0L
    }

    suspend fun add(
        postId: String,
        parentId: String?,
        username: String,
        receiver: String?,
        content: String,
    ) {
        mongo
            .commentCollection
            .insertOne(
                CommentModel(
                    id = ObjectId(),
                    parentId = parentId?.let { ObjectId(it) },
                    postId = postId,
                    username = username,
                    receiver = receiver,
                    upvoteUsers = emptySet(),
                    downvoteUsers = emptySet(),
                    content = content,
                )
            )
    }

    suspend fun upvote(commentId: String, username: String) {
        mongo
            .commentCollection
            .updateOne(
                CommentModel::id eq ObjectId(commentId),
                combine(
                    addToSet(CommentModel::upvoteUsers, username),
                    pull(CommentModel::downvoteUsers, username)
                )
            )
    }

    suspend fun cancelUpvote(commentId: String, username: String) {
        mongo
            .commentCollection
            .updateOne(
                CommentModel::id eq ObjectId(commentId),
                pull(CommentModel::upvoteUsers, username)
            )
    }

    suspend fun downvote(commentId: String, username: String) {
        mongo
            .commentCollection
            .updateOne(
                CommentModel::id eq ObjectId(commentId),
                combine(
                    addToSet(CommentModel::downvoteUsers, username),
                    pull(CommentModel::upvoteUsers, username)
                )
            )
    }

    suspend fun cancelDownvote(commentId: String, username: String) {
        mongo
            .commentCollection
            .updateOne(
                CommentModel::id eq ObjectId(commentId),
                pull(CommentModel::downvoteUsers, username)
            )
    }
}