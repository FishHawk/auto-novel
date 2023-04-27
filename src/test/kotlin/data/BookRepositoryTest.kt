package data

import appModule
import com.mongodb.client.model.Updates
import data.web.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.path
import org.litote.kmongo.pos
import org.litote.kmongo.setValue
import org.litote.kmongo.util.KMongoUtil.toBson
import java.io.File
import java.time.ZoneId

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repoEs by inject<EsBookMetadataRepository>(EsBookMetadataRepository::class.java)
    private val repoB by inject<BookMetadataRepository>(BookMetadataRepository::class.java)
    private val repoE by inject<BookEpisodeRepository>(BookEpisodeRepository::class.java)
    private val repoTMH by inject<WebBookTocMergeHistoryRepository>(WebBookTocMergeHistoryRepository::class.java)

    init {
        describe("test") {
            val h = repoTMH.findOne()!!
            println(h.providerId)
            println(h.bookId)
            println(h.reason)

//            repoTMH.col.deleteOne(
//                WebBookTocMergeHistoryRepository.TocMergedHistory::id eq h.id
//            )

//            repoB.setToc(h.providerId, h.bookId, h.tocOld)
//            repoB.setPauseUpdate(h.providerId, h.bookId, true)
            println(h.tocOld.size)
            println(h.tocNew.size)
//            h.tocNew.zip(h.tocOld).forEach {
//                if (it.first.titleJp != it.second.titleJp) {
//                    println()
//                    println(it.first.titleJp)
//                    println(it.second.titleJp)
//                }
//            }
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
