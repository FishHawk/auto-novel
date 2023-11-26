package infra

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import infra.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.path
import org.litote.kmongo.reactivestreams.KMongo
import java.util.concurrent.TimeUnit

@Serializable
data class WebNovelFavoriteModel(
    @Contextual val userId: Id<User>,
    @Contextual val novelId: Id<WebNovelMetadata>,
    @Contextual val favoredId: String,
    @Contextual val createAt: Instant,
    @Contextual val updateAt: Instant,
)

@Serializable
data class WenkuNovelFavoriteModel(
    @Contextual val userId: Id<User>,
    @Contextual val novelId: Id<WenkuNovelMetadata>,
    @Contextual val favoredId: String,
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

class DataSourceMongo(url: String) {
    val client = KMongo.createClient(url).coroutine
    private val database = client.getDatabase("main")

    // Common
    val articleCollection
        get() = database.getCollection<ArticleModel>("article")
    val commentCollection
        get() = database.getCollection<CommentModel>("comment-alt")

    val sakuraJobCollection
        get() = database.getCollection<SakuraJob>("gpu-job")
    val sakuraJobResultCollection
        get() = database.getCollection<SakuraJobResult>("gpu-job-result")
    val sakuraServerCollection
        get() = database.getCollection<SakuraServer>("sakura-server")
    val sakuraWebFailCaseCollection
        get() = database.getCollection<SakuraWebFailCase>("sakura-fail-case")
    val sakuraWenkuFailCaseCollection
        get() = database.getCollection<SakuraWenkuFailCase>("sakura-wenku-fail-case")


    val operationHistoryCollection
        get() = database.getCollection<OperationHistoryModel>("operation-history")

    val userCollectionName = "user"
    val userCollection
        get() = database.getCollection<User>(userCollectionName)

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

    val webNovelTocMergeHistoryCollection
        get() = database.getCollection<WebNovelTocMergeHistory>("toc-merge-history")

    // Wenku novel
    val wenkuNovelMetadataCollectionName = "wenku-metadata"
    val wenkuNovelMetadataCollection
        get() = database.getCollection<WenkuNovelMetadata>(wenkuNovelMetadataCollectionName)

    val wenkuNovelFavoriteCollection
        get() = database.getCollection<WenkuNovelFavoriteModel>("wenku-favorite")

    init {
        runBlocking {
            // Common
            articleCollection.ensureIndex(
                ArticleModel::updateAt,
                indexOptions = IndexOptions().partialFilterExpression(
                    Filters.eq(ArticleModel::pinned.path(), true)
                )
            )
            articleCollection.ensureIndex(
                ArticleModel::pinned,
                ArticleModel::updateAt,
            )
            commentCollection.ensureIndex(
                CommentModel::site,
                CommentModel::parent,
                CommentModel::id,
            )
            sakuraServerCollection.ensureUniqueIndex(
                SakuraServer::endpoint,
            )
            sakuraJobCollection.ensureUniqueIndex(
                SakuraJob::task,
            )
            operationHistoryCollection.ensureIndex(
                OperationHistoryModel::createAt,
            )

            userCollection.ensureUniqueIndex(User::email)
            userCollection.ensureUniqueIndex(User::username)


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
                WebNovelReadHistoryModel::userId,
                WebNovelReadHistoryModel::novelId,
            )
            webNovelReadHistoryCollection.ensureIndex(
                WebNovelReadHistoryModel::userId,
                WebNovelReadHistoryModel::createAt,
            )
            webNovelReadHistoryCollection.ensureIndex(
                WebNovelReadHistoryModel::createAt,
                indexOptions = IndexOptions().expireAfter(100, TimeUnit.DAYS),
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