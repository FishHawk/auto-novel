package infra

import appModule
import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.ktsearch.index
import infra.model.*
import infra.provider.WebNovelProviderDataSource
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelMetadataRepository
import infra.web.WebNovelTocMergeHistoryRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.Document
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.coroutine.projection
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val provider by inject<WebNovelProviderDataSource>(WebNovelProviderDataSource::class.java)
    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repoWNC by inject<WebNovelChapterRepository>(WebNovelChapterRepository::class.java)
    private val repoWNM by inject<WebNovelMetadataRepository>(WebNovelMetadataRepository::class.java)
    private val repoTMH by inject<WebNovelTocMergeHistoryRepository>(WebNovelTocMergeHistoryRepository::class.java)

    init {
        describe("test") {
            @Serializable
            data class WNMP(
                val providerId: String,
                val bookId: String,
                val titleJp: String,
                val titleZh: String? = null,
                val authors: List<WebNovelAuthor>,
                val type: WebNovelType = WebNovelType.连载中,
                val attentions: List<WebNovelAttention> = emptyList(),
                @Contextual val changeAt: LocalDateTime = LocalDateTime.now(),
            )

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
                    WebNovelMetadata::changeAt,
                )
                .skip(10 * 1000)
                .limit(1100)
                .toList()
            es.client.bulk(target = ElasticSearchDataSource.webNovelIndexName) {
                @Serializable
                data class ESU(val type: WebNovelType, val attentions: List<WebNovelAttention>)

                list.forEachIndexed { i, it ->
                    println("$i/${list.size} ${it.providerId}/${it.bookId}")
                    index(
                        doc = WebNovelMetadataEsModel(
                            providerId = it.providerId,
                            novelId = it.bookId,
                            authors = it.authors.map { it.name },
                            titleJp = it.titleJp,
                            titleZh = it.titleZh,
                            type = it.type,
                            attentions = it.attentions,
                            changeAt = it.changeAt.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
                        ),
                        id = "${it.providerId}.${it.bookId}",
                    )
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
