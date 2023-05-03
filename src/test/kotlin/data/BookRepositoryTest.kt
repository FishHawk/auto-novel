package data

import appModule
import com.mongodb.client.model.Updates
import data.web.*
import data.wenku.WenkuBookIndexRepository
import data.wenku.WenkuBookMetadataRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import kotlinx.datetime.Clock
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.pos
import org.litote.kmongo.setValue
import java.io.File
import java.time.ZoneId

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repoEs by inject<WebBookIndexRepository>(WebBookIndexRepository::class.java)
    private val repoB by inject<WebBookMetadataRepository>(WebBookMetadataRepository::class.java)
    private val repoE by inject<WebBookEpisodeRepository>(WebBookEpisodeRepository::class.java)
    private val repoTMH by inject<WebBookTocMergeHistoryRepository>(WebBookTocMergeHistoryRepository::class.java)

    private val repoWBM by inject<WenkuBookMetadataRepository>(WenkuBookMetadataRepository::class.java)
    private val repoWBI by inject<WenkuBookIndexRepository>(WenkuBookIndexRepository::class.java)

    init {
        describe("test") {
        }

        describe("kmongo issue 415") {
            println(setValue(BookEpisode::youdaoParagraphs.pos(0), "test").toBsonDocument())
            println(setValue(BookEpisode::baiduParagraphs.pos(0), "test").toBsonDocument())
            println(Updates.set("paragraphsZh.0", "test").toBsonDocument())
        }

        describe("script") {
            it("es同步") {
                val col = mongo.database.getCollection<BookMetadata>("metadata")
                val total = col.find().toList()
                repoEs.addBunch(total.map {
                    EsBookMetadata(
                        providerId = it.providerId,
                        bookId = it.bookId,
                        titleJp = it.titleJp,
                        titleZh = it.titleZh,
                        authors = it.authors.map { it.name },
                        changeAt = it.changeAt.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
                    )
                })
            }

            it("es同步-文库") {
                val col = mongo.database.getCollection<WenkuBookMetadataRepository.WenkuMetadata>("wenku-metadata")
                val total = col.find().toList().map {
                    WenkuBookIndexRepository.BookDocument(
                        id = it.id.toHexString(),
                        title = it.title,
                        titleZh = it.titleZh,
                        titleZhAlias = it.titleZhAlias,
                        cover = it.cover,
                        authors = it.authors,
                        artists = it.artists,
                        keywords = it.keywords,
                        updateAt = Clock.System.now().epochSeconds
                    )
                }
                repoWBI.addBunch(total)
            }

            it("sitemap") {
                val col = mongo.database.getCollection<BookMetadata>("metadata")
                val list = col.find().toList()
                File("sitemap.txt").printWriter().use { out ->
                    list.forEach {
                        out.println("https://books.fishhawk.top/novel/${it.providerId}/${it.bookId}")
                    }
                }
            }
        }
    }
}
