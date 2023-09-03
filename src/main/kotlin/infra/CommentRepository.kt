package infra

import com.mongodb.client.model.CountOptions
import infra.model.Comment
import infra.model.Page
import org.bson.types.ObjectId
import org.litote.kmongo.*

class CommentRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun list(
        postId: String,
        parentId: String?,
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
}