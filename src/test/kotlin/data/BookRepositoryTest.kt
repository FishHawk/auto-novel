package data

import appModule
import data.elasticsearch.ElasticSearchDataSource
import data.elasticsearch.EsBookMetadataRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repo by inject<EsBookMetadataRepository>(EsBookMetadataRepository::class.java)

    init {
        describe("test") {
            val a = repo.search("", null, 0, 10)
            println(a.total)
            println(a.items.size)
            a.items.forEach {
                println(it.titleJp)
            }
//            val col = mongo.database.getCollection<BookMetadata>("metadata")
//            val total = col.find().toList()
//            repo.addBunch(total.map {
//                EsBookMetadata(
//                    providerId = it.providerId,
//                    bookId = it.bookId,
//                    titleJp = it.titleJp,
//                    titleZh = it.titleZh,
//                    authors = it.authors.map { it.name },
//                    changeAt = it.changeAt.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
//                )
//            })
        }
    }
}
