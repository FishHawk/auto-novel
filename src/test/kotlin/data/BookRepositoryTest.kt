package data

import appModule
import data.web.EsBookMetadata
import data.web.EsBookMetadataRepository
import data.web.BookEpisode
import data.web.BookMetadata
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.eq
import java.io.File
import java.time.ZoneId

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repo by inject<EsBookMetadataRepository>(EsBookMetadataRepository::class.java)

    init {
        describe("test") {
            val col = mongo.database.getCollection<BookEpisode>("episode")
            col.find(
                BookEpisode::providerId eq "alphapolis",
            ).toList().map {
//                if (it.paragraphsJp.size < 5) {
//                    col.deleteOne(
//                        BookEpisode::providerId eq it.providerId,
//                        BookEpisode::bookId eq it.bookId,
//                        BookEpisode::episodeId eq it.episodeId,
//                    )
//                }
                println(it.paragraphs.size)
            }
        }

        describe("script") {
            it("es同步") {
                val col = mongo.database.getCollection<BookMetadata>("metadata")
                val total = col.find().toList()
                repo.addBunch(total.map {
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
