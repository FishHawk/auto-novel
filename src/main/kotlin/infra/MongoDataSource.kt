package infra

import com.mongodb.client.model.IndexOptions
import infra.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import util.PBKDF2
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@Serializable
data class CommentModel(
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
data class EmailCodeModel(
    val email: String,
    val emailCode: String,
    @Contextual val createdAt: Date,
)

class MongoDataSource(url: String) {
    val client = KMongo.createClient(url).coroutine
    val database = client.getDatabase("main")

    val emailCodeCollection
        get() = database.getCollection<EmailCodeModel>("email-code")

    init {
        runBlocking {
            emailCodeCollection.ensureIndex(
                EmailCodeModel::createdAt,
                indexOptions = IndexOptions().expireAfter(15, TimeUnit.MINUTES),
            )
        }
    }

    val userCollection
        get() = database.getCollection<User>("user")

    init {
        runBlocking {
            userCollection.ensureUniqueIndex(User::email)
            userCollection.ensureUniqueIndex(User::username)
        }
    }

    val commentCollection
        get() = database.getCollection<CommentModel>("comment")

    init {
        runBlocking {
            commentCollection.ensureIndex(
                CommentModel::postId,
                CommentModel::parentId,
                CommentModel::id,
            )
        }
    }

    //
    val webNovelMetadataCollection
        get() = database.getCollection<WebNovelMetadata>("metadata")

    init {
        runBlocking {
            webNovelMetadataCollection.ensureUniqueIndex(
                WebNovelMetadata::providerId,
                WebNovelMetadata::novelId,
            )
        }
    }

    val webNovelChapterCollection
        get() = database.getCollection<WebNovelChapter>("episode")

    init {
        runBlocking {
            webNovelChapterCollection.ensureUniqueIndex(
                WebNovelChapter::providerId,
                WebNovelChapter::novelId,
                WebNovelChapter::chapterId,
            )
        }
    }

    val webNovelPatchHistoryCollection
        get() = database.getCollection<WebNovelPatchHistory>("web-patch")

    init {
        runBlocking {
            webNovelPatchHistoryCollection.ensureUniqueIndex(
                WebNovelPatchHistory::providerId,
                WebNovelPatchHistory::novelId,
            )
        }
    }

    val webNovelTocMergeHistoryCollection
        get() = database.getCollection<WebNovelTocMergeHistory>("toc-merge-history")

    //
    val wenkuNovelMetadataCollection
        get() = database.getCollection<WenkuNovelMetadata>("wenku-metadata")
}