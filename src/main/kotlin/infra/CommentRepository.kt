package infra

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.*

@Serializable
private data class Comment(
    @Contextual @SerialName("_id") val id: ObjectId,
    @Contextual val parentId: ObjectId?,
    val postId: String,
    val username: String,
    val receiver: String?,
    val upvoteUsers: Set<String>,
    val downvoteUsers: Set<String>,
    val content: String,
)

@Serializable
data class CommentView(
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
    private val mongoDataSource: MongoDataSource,
) {
    private val col
        get() = mongoDataSource.database.getCollection<Comment>("comment")

    init {
        runBlocking {
            col.ensureIndex(
                Comment::postId,
                Comment::parentId,
                Comment::id,
            )
        }
    }

    suspend fun exist(
        id: String?,
    ): Boolean {
        return col.findOne(
            Comment::id eq ObjectId(id)
        ) != null
    }

    suspend fun add(
        postId: String,
        parentId: String?,
        username: String,
        receiver: String?,
        content: String,
    ) {
        col.insertOne(
            Comment(
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

    suspend fun list(
        postId: String,
        parentId: String?,
        viewer: String?,
        page: Int,
        pageSize: Int,
        reverse: Boolean = false,
    ): List<CommentView> {
        return col.find(
            Comment::postId eq postId,
            Comment::parentId eq parentId?.let { ObjectId(it) },
        )
            .let { if (reverse) it.descendingSort(Comment::id) else it }
            .skip(page * pageSize)
            .limit(pageSize)
            .toList()
            .map {
                CommentView(
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
    }

    suspend fun count(
        postId: String,
        parentId: String?,
    ): Long {
        return col.countDocuments(
            and(
                Comment::postId eq postId,
                Comment::parentId eq parentId?.let { ObjectId(it) },
            )
        )
    }

    suspend fun upvote(commentId: String, username: String) {
        col.updateOne(
            Comment::id eq ObjectId(commentId),
            combine(
                addToSet(Comment::upvoteUsers, username),
                pull(Comment::downvoteUsers, username)
            )
        )
    }

    suspend fun cancelUpvote(commentId: String, username: String) {
        col.updateOne(
            Comment::id eq ObjectId(commentId),
            pull(Comment::upvoteUsers, username)
        )
    }

    suspend fun downvote(commentId: String, username: String) {
        col.updateOne(
            Comment::id eq ObjectId(commentId),
            combine(
                addToSet(Comment::downvoteUsers, username),
                pull(Comment::upvoteUsers, username)
            )
        )
    }

    suspend fun cancelDownvote(commentId: String, username: String) {
        col.updateOne(
            Comment::id eq ObjectId(commentId),
            pull(Comment::downvoteUsers, username)
        )
    }
}