package infra

import com.mongodb.client.model.IndexOptions
import infra.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
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

@Serializable
data class ResetPasswordToken(
    @Contextual val userId: Id<User>,
    val token: String,
    @Contextual val createAt: Instant,
)

@Serializable
data class WebNovelFavoriteModel(
    @Contextual val userId: Id<User>,
    @Contextual val novelId: Id<WebNovelMetadata>,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
)

@Serializable
data class WenkuNovelFavoriteModel(
    @Contextual val userId: Id<User>,
    @Contextual val novelId: Id<WenkuNovelMetadata>,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
)

@Serializable
data class WebNovelReadHistoryModel(
    @Contextual val userId: Id<User>,
    @Contextual val novelId: Id<WenkuNovelMetadata>,
    @Contextual val chapterId: String,
    @Contextual val createAt: Instant,
)

class MongoDataSource(url: String) {
    val client = KMongo.createClient(url).coroutine
    val database = client.getDatabase("main")

    val emailCodeCollection
        get() = database.getCollection<EmailCodeModel>("email-code")
    val resetPasswordTokenCollection
        get() = database.getCollection<ResetPasswordToken>("reset-password-token")
    val userCollection
        get() = database.getCollection<User>("user")
    val commentCollection
        get() = database.getCollection<CommentModel>("comment")

    // Web novel
    val webNovelMetadataCollectionName = "metadata"
    val webNovelMetadataCollection
        get() = database.getCollection<WebNovelMetadata>(webNovelMetadataCollectionName)
    val webNovelChapterCollection
        get() = database.getCollection<WebNovelChapter>("episode")

    val webNovelFavoriteCollection
        get() = database.getCollection<WebNovelFavoriteModel>("web-favorite")
    val webNovelReadHistoryCollection
        get() = database.getCollection<WebNovelReadHistoryModel>("web-read-history")

    val webNovelPatchHistoryCollection
        get() = database.getCollection<WebNovelPatchHistory>("web-patch")
    val webNovelTocMergeHistoryCollection
        get() = database.getCollection<WebNovelTocMergeHistory>("toc-merge-history")

    // Wenku novel
    val wenkuNovelMetadataCollectionName = "wenku-metadata"
    val wenkuNovelMetadataCollection
        get() = database.getCollection<WenkuNovelMetadata>(wenkuNovelMetadataCollectionName)

    val wenkuNovelFavoriteCollection
        get() = database.getCollection<WenkuNovelFavoriteModel>("wenku-favorite")

    val wenkuNovelUploadHistoryCollection
        get() = database.getCollection<WenkuNovelUploadHistory>("wenku-upload-history")

    // Ensure index
    init {
        runBlocking {
            emailCodeCollection.ensureIndex(
                EmailCodeModel::createdAt,
                indexOptions = IndexOptions().expireAfter(15, TimeUnit.MINUTES),
            )
            resetPasswordTokenCollection.ensureUniqueIndex(ResetPasswordToken::userId)
            resetPasswordTokenCollection.ensureIndex(
                ResetPasswordToken::createAt,
                indexOptions = IndexOptions().expireAfter(15, TimeUnit.MINUTES),
            )
            userCollection.ensureUniqueIndex(User::email)
            userCollection.ensureUniqueIndex(User::username)

            commentCollection.ensureIndex(
                CommentModel::postId,
                CommentModel::parentId,
                CommentModel::id,
            )

            // Web novel
            webNovelMetadataCollection.ensureUniqueIndex(
                WebNovelMetadata::providerId,
                WebNovelMetadata::novelId,
            )
            webNovelChapterCollection.ensureUniqueIndex(
                WebNovelChapter::providerId,
                WebNovelChapter::novelId,
                WebNovelChapter::chapterId,
            )

            webNovelFavoriteCollection.ensureUniqueIndex(
                WebNovelFavoriteModel::userId,
                WebNovelFavoriteModel::novelId,
            )
            webNovelFavoriteCollection.ensureIndex(
                WebNovelFavoriteModel::userId,
                WebNovelFavoriteModel::createAt,
            )
            webNovelFavoriteCollection.ensureIndex(
                WebNovelFavoriteModel::userId,
                WebNovelFavoriteModel::updateAt,
            )

            webNovelReadHistoryCollection.ensureUniqueIndex(
                WebNovelFavoriteModel::userId,
                WebNovelFavoriteModel::novelId,
            )
            webNovelFavoriteCollection.ensureIndex(
                WebNovelFavoriteModel::userId,
                WebNovelFavoriteModel::createAt,
            )
            webNovelFavoriteCollection.ensureIndex(
                WebNovelFavoriteModel::createAt,
                indexOptions = IndexOptions().expireAfter(100, TimeUnit.DAYS),
            )

            webNovelPatchHistoryCollection.ensureUniqueIndex(
                WebNovelPatchHistory::providerId,
                WebNovelPatchHistory::novelId,
            )

            // Wenku novel
            wenkuNovelFavoriteCollection.ensureUniqueIndex(
                WenkuNovelFavoriteModel::userId,
                WenkuNovelFavoriteModel::novelId,
            )
            wenkuNovelFavoriteCollection.ensureIndex(
                WenkuNovelFavoriteModel::userId,
                WenkuNovelFavoriteModel::createAt,
            )
            wenkuNovelFavoriteCollection.ensureIndex(
                WenkuNovelFavoriteModel::userId,
                WenkuNovelFavoriteModel::updateAt,
            )
        }
    }
}