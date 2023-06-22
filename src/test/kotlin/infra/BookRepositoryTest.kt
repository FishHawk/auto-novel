package infra

import appModule
import infra.model.WebNovelMetadata
import infra.provider.WebNovelProviderDataSource
import infra.provider.providers.*
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelMetadataRepository
import infra.web.WebNovelTocMergeHistoryRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import io.ktor.client.plugins.*
import io.ktor.http.*
import org.bson.Document
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.*
import java.io.File

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
            val list = mongo
                .webNovelMetadataCollection
                .withDocumentClass<Document>()
                .find(
                    WebNovelMetadata::providerId eq Pixiv.id,
                    WebNovelMetadata::keywords exists false,
                )
                .projection(
                    WebNovelMetadata::providerId,
                    WebNovelMetadata::novelId,
                )
                .toList()
            list.forEachIndexed { i, it ->
                if (i < 120) return@forEachIndexed
                val pid = it["providerId"].toString()
                val nid = it["bookId"].toString()
                println("$i/${list.size} $pid/$nid")
                val rn = provider.getMetadata(pid, nid).getOrElse {
                    if (it is ClientRequestException) {
                        if (it.response.status == HttpStatusCode.NotFound) {
                            println("not found")
                            return@forEachIndexed
                        } else if (it.response.status == HttpStatusCode.TooManyRequests) {
                            return@describe
                        }
                    }
                    println(it.message)
                    return@forEachIndexed
                }
                mongo
                    .webNovelMetadataCollection
                    .updateOne(
                        WebNovelMetadata.byId(pid, nid),
                        combine(
                            setValue(WebNovelMetadata::type, rn.type),
                            setValue(WebNovelMetadata::attentions, rn.attentions),
                            setValue(WebNovelMetadata::keywords, rn.keywords),
                        )
                    )
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
