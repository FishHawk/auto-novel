package infra

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import infra.model.*
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.path
import org.litote.kmongo.reactivestreams.KMongo
import java.util.concurrent.TimeUnit

class DataSourceMongo(host: String, port: Int?) {
    val client = KMongo.createClient(
        "mongodb://${host}:${port ?: 27017}"
    ).coroutine
    private val database = client.getDatabase("main")

    // Common
    val articleCollection
        get() = database.getCollection<ArticleModel>("article")
    val commentCollection
        get() = database.getCollection<CommentModel>("comment-alt")

    val operationHistoryCollection
        get() = database.getCollection<OperationHistoryModel>("operation-history")

    // Sakura
    val sakuraJobCollection
        get() = database.getCollection<SakuraJob>("gpu-job")
    val sakuraJobResultCollection
        get() = database.getCollection<SakuraJobResult>("gpu-job-result")
    val sakuraServerCollection
        get() = database.getCollection<SakuraServer>("sakura-server")
    val sakuraWebIncorrectCaseCollection
        get() = database.getCollection<SakuraWebIncorrectCase>("sakura-incorrect-case")
    val sakuraWebFailCaseCollection
        get() = database.getCollection<SakuraWebFailCase>("sakura-fail-case")
    val sakuraWenkuFailCaseCollection
        get() = database.getCollection<SakuraWenkuFailCase>("sakura-wenku-fail-case")

    // User
    val userCollectionName = "user"
    val userCollection
        get() = database.getCollection<User>(userCollectionName)

    val userFavoredWebCollection
        get() = database.getCollection<UserFavoredWebNovelModel>("web-favorite")
    val userFavoredWenkuCollection
        get() = database.getCollection<UserFavoredWenkuNovelModel>("wenku-favorite")

    val userReadHistoryWebCollection
        get() = database.getCollection<UserReadHistoryWebModel>("web-read-history")

    // Web novel
    val webNovelMetadataCollectionName = "metadata"
    val webNovelMetadataCollection
        get() = database.getCollection<WebNovelMetadata>(webNovelMetadataCollectionName)
    val webNovelChapterCollection
        get() = database.getCollection<WebNovelChapter>("episode")

    val webNovelTocMergeHistoryCollection
        get() = database.getCollection<WebNovelTocMergeHistory>("toc-merge-history")

    // Wenku novel
    val wenkuNovelMetadataCollectionName = "wenku-metadata"
    val wenkuNovelMetadataCollection
        get() = database.getCollection<WenkuNovelMetadata>(wenkuNovelMetadataCollectionName)

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

            // Sakura
            sakuraServerCollection.ensureUniqueIndex(
                SakuraServer::endpoint,
            )
            sakuraJobCollection.ensureUniqueIndex(
                SakuraJob::task,
            )
            operationHistoryCollection.ensureIndex(
                OperationHistoryModel::createAt,
            )

            // User
            userCollection.ensureUniqueIndex(User::email)
            userCollection.ensureUniqueIndex(User::username)

            userFavoredWebCollection.ensureUniqueIndex(
                UserFavoredWebNovelModel::userId,
                UserFavoredWebNovelModel::novelId,
            )
            userFavoredWebCollection.ensureIndex(
                UserFavoredWebNovelModel::userId,
                UserFavoredWebNovelModel::createAt,
            )
            userFavoredWebCollection.ensureIndex(
                UserFavoredWebNovelModel::userId,
                UserFavoredWebNovelModel::updateAt,
            )

            userReadHistoryWebCollection.ensureUniqueIndex(
                UserReadHistoryWebModel::userId,
                UserReadHistoryWebModel::novelId,
            )
            userReadHistoryWebCollection.ensureIndex(
                UserReadHistoryWebModel::userId,
                UserReadHistoryWebModel::createAt,
            )
            userReadHistoryWebCollection.ensureIndex(
                UserReadHistoryWebModel::createAt,
                indexOptions = IndexOptions().expireAfter(100, TimeUnit.DAYS),
            )

            userFavoredWenkuCollection.ensureUniqueIndex(
                UserFavoredWenkuNovelModel::userId,
                UserFavoredWenkuNovelModel::novelId,
            )
            userFavoredWenkuCollection.ensureIndex(
                UserFavoredWenkuNovelModel::userId,
                UserFavoredWenkuNovelModel::createAt,
            )
            userFavoredWenkuCollection.ensureIndex(
                UserFavoredWenkuNovelModel::userId,
                UserFavoredWenkuNovelModel::updateAt,
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
        }
    }
}