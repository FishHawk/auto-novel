package infra

import appModule
import infra.web.*
import infra.wenku.WenkuNovelFileRepository
import infra.wenku.WenkuNovelIndexRepository
import infra.wenku.WenkuNovelMetadataRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import kotlinx.datetime.Clock
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import java.io.File
import java.time.ZoneId

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repoEs by inject<WebNovelIndexRepository>(WebNovelIndexRepository::class.java)
    private val repoB by inject<WebNovelMetadataRepository>(WebNovelMetadataRepository::class.java)
    private val repoE by inject<WebChapterRepository>(WebChapterRepository::class.java)
    private val repoTMH by inject<WebNovelTocMergeHistoryRepository>(WebNovelTocMergeHistoryRepository::class.java)

    private val repoWBM by inject<WenkuNovelMetadataRepository>(WenkuNovelMetadataRepository::class.java)
    private val repoWBI by inject<WenkuNovelIndexRepository>(WenkuNovelIndexRepository::class.java)
    private val repoWBF by inject<WenkuNovelFileRepository>(WenkuNovelFileRepository::class.java)

    init {
        describe("test") {
//            repoWBF.unpackEpub("non-archived", "test.epub",)
            repoWBF.makeFile("non-archived", "test.epub", NovelFileLang.MIX_YOUDAO)
            repoWBF.makeFile("non-archived", "test.epub", NovelFileLang.ZH_YOUDAO)
        }

        describe("kmongo issue 415") {
//            println(setValue(BookEpisode::youdaoParagraphs.pos(0), "test").toBsonDocument())
//            println(setValue(BookEpisode::baiduParagraphs.pos(0), "test").toBsonDocument())
//            println(Updates.set("paragraphsZh.0", "test").toBsonDocument())
        }

        describe("script") {
            it("es同步") {
                val col = mongo.database.getCollection<WebNovelMetadataRepository.NovelMetadata>("metadata")
                val total = col.find().toList()
                repoEs.addBunch(total.map {
                    NovelMetadata(
                        providerId = it.providerId,
                        novelId = it.novelId,
                        titleJp = it.titleJp,
                        titleZh = it.titleZh,
                        authors = it.authors.map { it.name },
                        changeAt = it.changeAt.atZone(ZoneId.systemDefault()).toInstant().epochSecond,
                    )
                })
            }

            it("es同步-文库") {
                val col = mongo.database.getCollection<WenkuNovelMetadataRepository.NovelMetadata>("wenku-metadata")
                val total = col.find().toList().map {
                    WenkuNovelIndexRepository.Novel(
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
                val col = mongo.database.getCollection<WebNovelMetadataRepository.NovelMetadata>("metadata")
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
