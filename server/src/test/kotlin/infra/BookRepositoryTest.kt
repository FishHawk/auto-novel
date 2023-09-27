package infra

import appModule
import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.ktsearch.index
import infra.model.*
import infra.provider.WebNovelProviderDataSource
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.coroutine.projection
import org.litote.kmongo.eq
import org.litote.kmongo.id.toId
import org.litote.kmongo.setValue
import java.io.File

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val provider by inject<WebNovelProviderDataSource>(WebNovelProviderDataSource::class.java)
    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val wenkuVR by inject<WenkuNovelVolumeRepository>(WenkuNovelVolumeRepository::class.java)
    private val wenkuMR by inject<WenkuNovelMetadataRepository>(WenkuNovelMetadataRepository::class.java)
    private val userR by inject<UserRepository>(UserRepository::class.java)

    init {
        describe("test") {
            val nid = "6450c44884972153850fa845"
//            mongo.wenkuNovelFavoriteCollection.find(
//                WenkuNovelFavoriteModel::novelId eq ObjectId(nid).toId()
//            ).toList().forEach { println(it) }
//
//            mongo.wenkuNovelFavoriteCollection.find(
//                WenkuNovelFavoriteModel::novelId eq ObjectId("65119daeee167f34bff89b1e").toId()
//            ).toList().forEach { println(it) }

            mongo.wenkuNovelFavoriteCollection.updateMany(
                WenkuNovelFavoriteModel::novelId eq ObjectId(nid).toId(),
                setValue(WenkuNovelFavoriteModel::novelId, ObjectId("65119daeee167f34bff89b1e").toId())
            )
            wenkuMR.delete(nid)
        }
        describe("build es index") {
            @Serializable
            data class WNMP(
                val providerId: String,
                val bookId: String,
                val titleJp: String,
                val titleZh: String? = null,
                val authors: List<WebNovelAuthor>,
                val type: WebNovelType = WebNovelType.连载中,
                val attentions: List<WebNovelAttention> = emptyList(),
                val keywords: List<String> = emptyList(),
                val gpt: Long = 0,
                val toc: List<WebNovelTocItem>,
                @Contextual val updateAt: Instant,
            )

            suspend fun buildIndex(skip: Int, limit: Int): Int {
                val list = mongo
                    .webNovelMetadataCollection
                    .withDocumentClass<WNMP>()
                    .find()
                    .projection(
                        WebNovelMetadata::providerId,
                        WebNovelMetadata::novelId,
                        WebNovelMetadata::titleJp,
                        WebNovelMetadata::titleZh,
                        WebNovelMetadata::authors,
                        WebNovelMetadata::type,
                        WebNovelMetadata::attentions,
                        WebNovelMetadata::keywords,
                        WebNovelMetadata::gpt,
                        WebNovelMetadata::toc,
                        WebNovelMetadata::updateAt,
                    )
                    .skip(skip)
                    .limit(limit)
                    .toList()
                es.client.bulk(target = ElasticSearchDataSource.webNovelIndexName) {
                    list.forEach { it ->
                        index(
                            doc = WebNovelMetadataEsModel(
                                providerId = it.providerId,
                                novelId = it.bookId,
                                authors = it.authors.map { it.name },
                                titleJp = it.titleJp,
                                titleZh = it.titleZh,
                                type = it.type,
                                attentions = it.attentions,
                                keywords = it.keywords,
                                hasGpt = it.gpt > 0,
                                tocSize = it.toc.count { it.chapterId != null },
                                updateAt = it.updateAt.epochSeconds,
                            ),
                            id = "${it.providerId}.${it.bookId}",
                        )
                    }
                }
                return list.size
            }

            var batch = 0
            val batchSize = 500
            while (true) {
                println("batch${batch} start")
                val actualBatchSize = buildIndex(
                    batch * batchSize,
                    batchSize + 50,
                )
                println("batch${batch} finish ${actualBatchSize}")

                if (actualBatchSize == batchSize + 50) {
                    batch += 1
                } else {
                    break
                }
            }
        }

        describe("kmongo issue 415") {
//            println(setValue(BookEpisode::youdaoParagraphs.pos(0), "test").toBsonDocument())
//            println(setValue(BookEpisode::baiduParagraphs.pos(0), "test").toBsonDocument())
//            println(Updates.set("paragraphsZh.0", "test").toBsonDocument())
        }

        describe("generate sitemap") {
            File("sitemap.txt").printWriter().use { out ->
                mongo
                    .webNovelMetadataCollection
                    .withDocumentClass<Document>()
                    .find()
                    .projection(
                        WebNovelMetadata::providerId,
                        WebNovelMetadata::novelId,
                    )
                    .toList()
                    .forEach {
                        val pid = it.getString("providerId")
                        val nid = it.getString("bookId")
                        out.println("https://books.fishhawk.top/novel/${pid}/${nid}")
                    }

                mongo
                    .wenkuNovelMetadataCollection
                    .projection(WenkuNovelMetadata::id)
                    .toList()
                    .forEach {
                        val nid = it.toHexString()
                        out.println("https://books.fishhawk.top/wenku/${nid}")
                    }
            }
        }
    }
}
