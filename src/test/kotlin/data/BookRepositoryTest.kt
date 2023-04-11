package data

import appModule
import data.web.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import java.io.File
import java.time.ZoneId

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repoEs by inject<EsBookMetadataRepository>(EsBookMetadataRepository::class.java)
    private val repoB by inject<BookMetadataRepository>(BookMetadataRepository::class.java)
    private val repoE by inject<BookEpisodeRepository>(BookEpisodeRepository::class.java)

    init {
        describe("test") {
            val pid = "syosetu"
            val bid = "n1306fy"
            val toc = repoB.get(pid, bid).getOrNull()!!.toc
            (69..79).forEach {
                repoE.delete(pid, bid, it.toString())
            }
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
