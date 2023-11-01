package infra

import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.ktsearch.index
import infra.model.*
import infra.web.DataSourceWebNovelProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bson.types.ObjectId
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.coroutine.projection
import sakura.sakuraTranslateSeg
import java.io.File


val appModule = module {
    // Data Source
    single {
        val mongodbUrl = System.getenv("MONGODB_URL") ?: "mongodb://192.168.1.110:27017"
        DataSourceMongo(mongodbUrl)
    }
    single {
        val url = System.getenv("ELASTIC_SEARCH_DB_URL") ?: "192.168.1.110"
        DataSourceElasticSearch(url)
    }
    single {
        val httpProxy: String? = System.getenv("HTTPS_PROXY")
        val pixivPhpsessid: String? = System.getenv("PIXIV_COOKIE_PHPSESSID")
        DataSourceWebNovelProvider(
            httpsProxy = httpProxy,
            pixivPhpsessid = pixivPhpsessid,
        )
    }
}

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(
        KoinExtension(
            module = appModule,
            mode = KoinLifecycleMode.Root,
        )
    )

    private val provider by inject<DataSourceWebNovelProvider>(DataSourceWebNovelProvider::class.java)
    private val es by inject<DataSourceElasticSearch>(DataSourceElasticSearch::class.java)
    private val mongo by inject<DataSourceMongo>(DataSourceMongo::class.java)

    init {
        describe("test") {
            val client = HttpClient(Java) {
                install(ContentNegotiation) {
                    json(Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                expectSuccess = true

            }
            val text = """「ぽぉおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおおぁあああああああああおおおおおおおおんっ！！」
俺たちは、同時に――手を出した。""".lines()
            sakuraTranslateSeg(
                client,
                "http://192.168.1.162:5000/api/v1/generate",
                seg = text,
            ) { _, _ ->
            }
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
                val visited: Long = 0,
                val gpt: Long = 0,
                val sakura: Long = 0,
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
                        WebNovelMetadata::sakura,
                        WebNovelMetadata::visited,
                        WebNovelMetadata::toc,
                        WebNovelMetadata::updateAt,
                    )
                    .skip(skip)
                    .limit(limit)
                    .toList()
                es.client.bulk(target = DataSourceElasticSearch.webNovelIndexName) {
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
                                hasSakura = it.sakura > 0,
                                visited = it.visited.toInt(),
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

        describe("build wenku es index") {
            @Serializable
            data class WNMP(
                @Contextual @SerialName("_id") val id: ObjectId,
                val title: String,
                val titleZh: String,
                val cover: String,
                val authors: List<String>,
                val artists: List<String>,
                val keywords: List<String>,
                val r18: Boolean = false,
                @Contextual val updateAt: Instant,
            )

            suspend fun buildIndex(skip: Int, limit: Int): Int {
                val list = mongo
                    .wenkuNovelMetadataCollection
                    .withDocumentClass<WNMP>()
                    .find()
                    .projection(
                        WenkuNovelMetadata::id,
                        WenkuNovelMetadata::title,
                        WenkuNovelMetadata::titleZh,
                        WenkuNovelMetadata::cover,
                        WenkuNovelMetadata::authors,
                        WenkuNovelMetadata::artists,
                        WenkuNovelMetadata::keywords,
                        WenkuNovelMetadata::r18,
                        WenkuNovelMetadata::updateAt,
                    )
                    .skip(skip)
                    .limit(limit)
                    .toList()
                es.client.bulk(target = DataSourceElasticSearch.wenkuNovelIndexName) {
                    list.forEach {
                        index(
                            doc = WenkuNovelMetadataEsModel(
                                id = it.id.toHexString(),
                                title = it.title,
                                titleZh = it.titleZh,
                                cover = it.cover,
                                authors = it.authors,
                                artists = it.artists,
                                keywords = it.keywords,
                                r18 = it.r18,
                                updateAt = it.updateAt.epochSeconds,
                            ),
                            id = it.id.toHexString(),
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
