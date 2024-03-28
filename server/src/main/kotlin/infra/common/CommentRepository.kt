package infra.common

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import domain.entity.*
import infra.DataSourceMongo
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId

class CommentRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun listCommentWithUser(
        site: String,
        parent: ObjectId?,
        page: Int,
        pageSize: Int,
        reverse: Boolean = false,
    ): Page<CommentWithUserReadModel> {
        @Serializable
        data class CommentPage(
            val total: Int = 0,
            val items: List<CommentWithUserReadModel>,
        )

        val doc = mongo
            .commentCollection
            .aggregate<CommentPage>(
                match(
                    and(
                        Comment::site eq site,
                        Comment::parent eq parent?.toId(),
                    ),
                ),
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        sort(
                            if (reverse) descending(Comment::id)
                            else ascending(Comment::id),
                        ),
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.userCollectionName,
                            localField = Comment::user.path(),
                            foreignField = User::id.path(),
                            newAs = CommentWithUserReadModel::user.path(),
                        ),
                        unwind(CommentWithUserReadModel::user.path().projection),
                        project(
                            CommentWithUserReadModel::id,
                            CommentWithUserReadModel::site,
                            CommentWithUserReadModel::content,
                            CommentWithUserReadModel::hidden,
                            CommentWithUserReadModel::numReplies,
                            CommentWithUserReadModel::user / UserOutline::username,
                            CommentWithUserReadModel::user / UserOutline::role,
                            CommentWithUserReadModel::createAt,
                        )
                    )
                ),
                project(
                    CommentPage::total from arrayElemAt("count.count".projection, 0),
                    CommentPage::items from "items".projection,
                )
            )
            .first()
        return if (doc == null) {
            emptyPage()
        } else {
            Page(
                items = doc.items,
                total = doc.total.toLong(),
                pageSize = pageSize,
            )
        }
    }

    suspend fun deleteComment(
        id: ObjectId,
    ): Boolean =
        mongo
            .commentCollection
            .deleteOne(Comment::id eq id)
            .run { deletedCount > 0 }

    suspend fun deleteCommentBySite(
        site: String,
    ) {
        mongo
            .commentCollection
            .deleteMany(Comment::site eq site)
    }

    suspend fun deleteCommentByParent(
        parent: ObjectId,
    ) {
        mongo
            .commentCollection
            .deleteMany(Comment::parent eq parent.toId())
    }

    suspend fun createComment(
        site: String,
        parent: ObjectId?,
        user: ObjectId,
        content: String,
    ): ObjectId =
        mongo
            .commentCollection
            .insertOne(
                Comment(
                    id = ObjectId(),
                    site = site,
                    content = content,
                    numReplies = 0,
                    parent = parent?.toId(),
                    user = user.toId(),
                    createAt = Clock.System.now(),
                )
            )
            .run { insertedId!!.asObjectId().value }

    suspend fun increaseNumReplies(
        id: ObjectId,
    ): Boolean =
        mongo
            .commentCollection
            .updateOne(
                Comment::id eq id,
                inc(Comment::numReplies, 1),
            )
            .run { matchedCount > 0 }

    suspend fun updateCommentHidden(
        id: ObjectId,
        hidden: Boolean,
    ): Boolean =
        mongo
            .commentCollection
            .updateOne(
                Comment::id eq id,
                setValue(Comment::hidden, hidden),
            )
            .run { matchedCount > 0 }
}