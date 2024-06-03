package infra

import domain.entity.Comment
import infra.common.SakuraJobRepository
import infra.user.UserRepository
import infra.web.DataSourceWebNovelProvider
import infra.wenku.WenkuNovelMetadataRepository
import io.kotest.core.spec.style.DescribeSpec
import org.koin.test.KoinTest
import org.koin.test.inject
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.setValue

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()

    private val provider by inject<DataSourceWebNovelProvider>()
    private val es by inject<DataSourceElasticSearch>()
    private val mongo by inject<DataSourceMongo>()

    private val sjRepo by inject<SakuraJobRepository>()
    private val userRepo by inject<UserRepository>()
    private val wenkuRepo by inject<WenkuNovelMetadataRepository>()

    init {
        describe("test") {
            mongo.commentCollection.updateMany(
                EMPTY_BSON,
                setValue(Comment::hidden, false)
            )
        }
//        describe("build es index") {
//            @Serializable
//            data class WNMP(
//                val providerId: String,
//                val bookId: String,
//                val titleJp: String,
//                val titleZh: String? = null,
//                val authors: List<WebNovelAuthor>,
//                val type: WebNovelType = WebNovelType.连载中,
//                val attentions: List<WebNovelAttention> = emptyList(),
//                val keywords: List<String> = emptyList(),
//                val visited: Long = 0,
//                val gpt: Long = 0,
//                val sakura: Long = 0,
//                val toc: List<WebNovelTocItem>,
//                @Contextual val updateAt: Instant,
//            )
//
//            suspend fun buildIndex(skip: Int, limit: Int): Int {
//                val list = mongo
//                    .webNovelMetadataCollection
//                    .withDocumentClass<WNMP>()
//                    .find()
//                    .projection(
//                        WebNovelMetadata::providerId,
//                        WebNovelMetadata::novelId,
//                        WebNovelMetadata::titleJp,
//                        WebNovelMetadata::titleZh,
//                        WebNovelMetadata::authors,
//                        WebNovelMetadata::type,
//                        WebNovelMetadata::attentions,
//                        WebNovelMetadata::keywords,
//                        WebNovelMetadata::gpt,
//                        WebNovelMetadata::sakura,
//                        WebNovelMetadata::visited,
//                        WebNovelMetadata::toc,
//                        WebNovelMetadata::updateAt,
//                    )
//                    .skip(skip)
//                    .limit(limit)
//                    .toList()
//                es.client.bulk(target = DataSourceElasticSearch.webNovelIndexName) {
//                    list.forEach { it ->
//                        index(
//                            doc = WebNovelMetadataEsModel(
//                                providerId = it.providerId,
//                                novelId = it.bookId,
//                                authors = it.authors.map { it.name },
//                                titleJp = it.titleJp,
//                                titleZh = it.titleZh,
//                                type = it.type,
//                                attentions = it.attentions,
//                                keywords = it.keywords,
//                                hasGpt = it.gpt > 0,
//                                hasSakura = it.sakura > 0,
//                                visited = it.visited.toInt(),
//                                tocSize = it.toc.count { it.chapterId != null },
//                                updateAt = it.updateAt.epochSeconds,
//                            ),
//                            id = "${it.providerId}.${it.bookId}",
//                        )
//                    }
//                }
//                return list.size
//            }
//
//            var batch = 0
//            val batchSize = 500
//            while (true) {
//                println("batch${batch} start")
//                val actualBatchSize = buildIndex(
//                    batch * batchSize,
//                    batchSize + 50,
//                )
//                println("batch${batch} finish ${actualBatchSize}")
//
//                if (actualBatchSize == batchSize + 50) {
//                    batch += 1
//                } else {
//                    break
//                }
//            }
//        }
    }
}
