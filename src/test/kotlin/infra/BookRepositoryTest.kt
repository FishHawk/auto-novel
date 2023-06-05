package infra

import appModule
import infra.model.TranslatorId
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelMetadataRepository
import infra.web.WebNovelTocMergeHistoryRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import java.io.File

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repoWNC by inject<WebNovelChapterRepository>(WebNovelChapterRepository::class.java)
    private val repoWNM by inject<WebNovelMetadataRepository>(WebNovelMetadataRepository::class.java)
    private val repoTMH by inject<WebNovelTocMergeHistoryRepository>(WebNovelTocMergeHistoryRepository::class.java)

    init {
        describe("test") {
            @Serializable
            data class TT(val providerId: String, val bookId: String)

            val ids = mongo.webNovelMetadataCollection.withDocumentClass<TT>().find().toList()
            val size = ids.size
            ids.forEachIndexed { index, it ->
                println("$index/$size  ${it.providerId}/${it.bookId}")
                repoWNM.updateTranslateStateJp(it.providerId, it.bookId)
                repoWNM.updateTranslateStateZh(it.providerId, it.bookId, TranslatorId.Baidu)
                repoWNM.updateTranslateStateZh(it.providerId, it.bookId, TranslatorId.Youdao)
            }
        }

        describe("kmongo issue 415") {
//            println(setValue(BookEpisode::youdaoParagraphs.pos(0), "test").toBsonDocument())
//            println(setValue(BookEpisode::baiduParagraphs.pos(0), "test").toBsonDocument())
//            println(Updates.set("paragraphsZh.0", "test").toBsonDocument())
        }

        describe("script") {
            it("sitemap") {
                val col = mongo.webNovelMetadataCollection
                val list = col.find().toList()
                File("sitemap.txt").printWriter().use { out ->
                    list.forEach {
                        out.println("https://books.fishhawk.top/novel/${it.providerId}/${it.novelId}")
                    }
                }
            }
        }
    }
}
