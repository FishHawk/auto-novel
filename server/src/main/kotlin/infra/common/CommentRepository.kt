package infra.common

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Facet
import infra.DataSourceMongo
import infra.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import org.litote.kmongo.id.toId

class CommentRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun listComment(
        site: String,
        parent: ObjectId?,
        page: Int,
        pageSize: Int,
        reverse: Boolean = false,
    ): Page<Comment> {
        @Serializable
        data class CommentPage(val total: Int = 0, val items: List<Comment>)

        val doc = mongo
            .commentCollection
            .aggregate<CommentPage>(
                match(
                    and(
                        CommentModel::site eq site,
                        CommentModel::parent eq parent?.toId(),
                    ),
                ),
                facet(
                    Facet("count", Aggregates.count()),
                    Facet(
                        "items",
                        sort(
                            if (reverse) descending(CommentModel::id)
                            else ascending(CommentModel::id),
                        ),
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            from = mongo.userCollectionName,
                            localField = CommentModel::user.path(),
                            foreignField = User::id.path(),
                            newAs = Comment::user.path(),
                        ),
                        unwind(Comment::user.path().projection),
                        project(
                            Comment::id,
                            Comment::site,
                            Comment::content,
                            Comment::numReplies,
                            Comment::user / UserOutline::username,
                            Comment::user / UserOutline::role,
                            Comment::createAt,
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

    suspend fun createComment(
        site: String,
        parent: ObjectId?,
        user: ObjectId,
        content: String,
    ) {
        mongo
            .commentCollection
            .insertOne(
                CommentModel(
                    id = ObjectId(),
                    site = site,
                    content = content,
                    numReplies = 0,
                    parent = parent?.toId(),
                    user = user.toId(),
                    createAt = Clock.System.now(),
                )
            )
    }

    suspend fun increaseNumReplies(id: ObjectId): Boolean {
        val updateResult = mongo
            .commentCollection
            .updateOne(
                CommentModel::id eq id,
                inc(CommentModel::numReplies, 1),
            )
        return updateResult.matchedCount > 0
    }
}