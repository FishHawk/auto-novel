package infra

import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Updates.set
import infra.web.WebNovelFavoriteDbModel
import io.kotest.core.spec.style.DescribeSpec
import koinExtensions
import kotlinx.coroutines.flow.count
import kotlinx.datetime.Instant
import org.bson.Document
import org.koin.test.KoinTest
import org.koin.test.inject

class MongoClientTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val mongo by inject<MongoClient>()

    init {
        describe("临时测试") {
            val userFavoredWebCollection =
                mongo.database.getCollection<WebNovelFavoriteDbModel>(
                    MongoCollectionNames.WEB_FAVORITE,
                )

            val total = userFavoredWebCollection.withDocumentClass<Document>().find(
                type(WebNovelFavoriteDbModel::updateAt.field(), "string"),
            ).count()

            var i = 0
            userFavoredWebCollection.withDocumentClass<Document>().find(
                type(WebNovelFavoriteDbModel::updateAt.field(), "string"),
            ).collect {
                if (i % 100 == 0) {
                    println("${i}/${total}")
                }
                i += 1
                val userId = it.getObjectId("userId")
                val novelId = it.getObjectId("novelId")
                val updateAt = it.getString("updateAt")

                userFavoredWebCollection.updateOne(
                    and(
                        eq(WebNovelFavoriteDbModel::userId.field(), userId),
                        eq(WebNovelFavoriteDbModel::novelId.field(), novelId),
                    ),
                    set(
                        WebNovelFavoriteDbModel::updateAt.field(),
                        Instant.parse(updateAt)
                    )
                )
            }
        }
    }
}