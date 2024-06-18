package infra.comment

import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.Facet
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Projections.*
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.client.model.Sorts.descending
import com.mongodb.client.model.Updates.inc
import com.mongodb.client.model.Updates.set
import infra.*
import infra.common.Page
import infra.common.emptyPage
import infra.user.User
import infra.user.UserOutline
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

class CommentRepository(
    mongo: MongoClient,
) {
    private val commentCollection =
        mongo.database.getCollection<CommentDbModel>(
            MongoCollectionNames.COMMENT,
        )

    suspend fun listCommentWithUser(
        site: String,
        parent: ObjectId?,
        page: Int,
        pageSize: Int,
        reverse: Boolean = false,
    ): Page<Comment> {
        @Serializable
        data class PageModel(
            val total: Int = 0,
            val items: List<Comment>,
        )

        val doc = commentCollection
            .aggregate<PageModel>(
                match(
                    and(
                        eq(CommentDbModel::site.field(), site),
                        eq(CommentDbModel::parent.field(), parent),
                    ),
                ),
                facet(
                    Facet("count", count()),
                    Facet(
                        "items",
                        sort(
                            if (reverse) descending(CommentDbModel::id.field())
                            else ascending(CommentDbModel::id.field()),
                        ),
                        skip(page * pageSize),
                        limit(pageSize),
                        lookup(
                            /* from = */ MongoCollectionNames.USER,
                            /* localField = */ CommentDbModel::user.field(),
                            /* foreignField = */ User::id.field(),
                            /* as = */ Comment::user.field(),
                        ),
                        unwind(Comment::user.fieldPath()),
                        project(
                            fields(
                                include(
                                    Comment::id.field(),
                                    Comment::site.field(),
                                    Comment::content.field(),
                                    Comment::hidden.field(),
                                    Comment::numReplies.field(),
                                    Comment::user.field() + "." + UserOutline::username.field(),
                                    Comment::user.field() + "." + UserOutline::role.field(),
                                    Comment::createAt.field(),
                                )
                            )
                        )
                    )
                ),
                project(
                    fields(
                        computed(PageModel::total.field(), arrayElemAt("count.count", 0)),
                        include(PageModel::items.field()),
                    )
                )
            )
            .firstOrNull()

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
        commentCollection
            .deleteOne(
                eq(CommentDbModel::id.field(), id),
            )
            .run { deletedCount > 0 }

    suspend fun deleteCommentBySite(
        site: String,
    ) {
        commentCollection
            .deleteMany(
                eq(CommentDbModel::site.field(), site),
            )
    }

    suspend fun deleteCommentByParent(
        parent: ObjectId,
    ) {
        commentCollection
            .deleteMany(
                eq(CommentDbModel::parent.field(), parent),
            )
    }

    suspend fun createComment(
        site: String,
        parent: ObjectId?,
        user: ObjectId,
        content: String,
    ): ObjectId =
        commentCollection
            .insertOne(
                CommentDbModel(
                    id = ObjectId(),
                    site = site,
                    content = content,
                    numReplies = 0,
                    parent = parent,
                    user = user,
                    createAt = Clock.System.now(),
                )
            )
            .run { insertedId!!.asObjectId().value }

    suspend fun increaseNumReplies(
        id: ObjectId,
    ): Boolean =
        commentCollection
            .updateOne(
                eq(CommentDbModel::id.field(), id),
                inc(CommentDbModel::numReplies.field(), 1),
            )
            .run { matchedCount > 0 }

    suspend fun updateCommentHidden(
        id: ObjectId,
        hidden: Boolean,
    ): Boolean =
        commentCollection
            .updateOne(
                eq(CommentDbModel::id.field(), id),
                set(CommentDbModel::hidden.field(), hidden),
            )
            .run { matchedCount > 0 }
}