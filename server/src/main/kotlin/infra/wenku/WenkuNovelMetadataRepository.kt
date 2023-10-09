package infra.wenku

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import com.mongodb.client.model.CountOptions
import infra.ElasticSearchDataSource
import infra.MongoDataSource
import infra.WenkuNovelMetadataEsModel
import infra.model.Page
import infra.model.WebNovelMetadata
import infra.model.WenkuNovelMetadata
import infra.model.WenkuNovelMetadataOutline
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.combine
import org.litote.kmongo.inc
import org.litote.kmongo.setValue
import java.util.*

class WenkuNovelMetadataRepository(
    private val mongo: MongoDataSource,
    private val es: ElasticSearchDataSource,
) {
    suspend fun search(
        userQuery: String?,
        page: Int,
        pageSize: Int,
    ): Page<WenkuNovelMetadataOutline> {
        val response = es.client.search(
            ElasticSearchDataSource.wenkuNovelIndexName,
            from = page * pageSize,
            size = pageSize
        ) {
            query = bool {
                if (userQuery != null) {
                    must(
                        simpleQueryString(
                            userQuery,
                            WenkuNovelMetadataEsModel::title,
                            WenkuNovelMetadataEsModel::titleZh,
                            WenkuNovelMetadataEsModel::authors,
                            WenkuNovelMetadataEsModel::artists,
                            WenkuNovelMetadataEsModel::keywords,
                        ) {
                            defaultOperator = MatchOperator.AND
                        }
                    )
                } else {
                    sort {
                        add(WenkuNovelMetadataEsModel::updateAt)
                    }
                }
            }
        }
        val items = response.hits?.hits
            ?.map { hit ->
                val model = hit.parseHit<WenkuNovelMetadataEsModel>()
                WenkuNovelMetadataOutline(
                    id = model.id,
                    title = model.title,
                    titleZh = model.titleZh,
                    cover = model.cover,
                )
            }
            ?: emptyList()
        val total = response.total
        return Page(items = items, total = total)
    }

    suspend fun exist(novelId: String): Boolean {
        return mongo
            .wenkuNovelMetadataCollection
            .countDocuments(
                WenkuNovelMetadata.byId(novelId),
                CountOptions().limit(1),
            ) != 0L
    }

    suspend fun get(novelId: String): WenkuNovelMetadata? {
        return mongo
            .wenkuNovelMetadataCollection
            .findOne(WenkuNovelMetadata.byId(novelId))
    }

    suspend fun create(
        title: String,
        titleZh: String,
        cover: String,
        coverSmall: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
        introduction: String,
    ): String {
        val insertResult = mongo
            .wenkuNovelMetadataCollection
            .insertOne(
                WenkuNovelMetadata(
                    id = ObjectId(),
                    title = title,
                    titleZh = titleZh,
                    cover = cover,
                    coverSmall = coverSmall,
                    authors = authors,
                    artists = artists,
                    keywords = keywords,
                    introduction = introduction,
                    visited = 0,
                )
            )
        val id = insertResult.insertedId!!.asObjectId().value.toHexString()
        syncEs(
            novelId = id,
            title = title,
            titleZh = titleZh,
            cover = coverSmall,
            authors = authors,
            artists = artists,
            keywords = keywords,
        )
        return id
    }

    suspend fun update(
        novelId: String,
        title: String,
        titleZh: String,
        cover: String,
        coverSmall: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
        introduction: String,
    ) {
        mongo
            .wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                combine(
                    listOf(
                        setValue(WenkuNovelMetadata::title, title),
                        setValue(WenkuNovelMetadata::titleZh, titleZh),
                        setValue(WenkuNovelMetadata::cover, cover),
                        setValue(WenkuNovelMetadata::coverSmall, coverSmall),
                        setValue(WenkuNovelMetadata::authors, authors),
                        setValue(WenkuNovelMetadata::artists, artists),
                        setValue(WenkuNovelMetadata::keywords, keywords),
                        setValue(WenkuNovelMetadata::introduction, introduction),
                    )
                )
            )
        syncEs(
            novelId = novelId,
            title = title,
            titleZh = titleZh,
            cover = coverSmall,
            authors = authors,
            artists = artists,
            keywords = keywords,
        )
    }

    suspend fun delete(
        novelId: String,
    ) {
        mongo
            .wenkuNovelMetadataCollection
            .deleteOne(
                WenkuNovelMetadata.byId(novelId),
            )
        es.client.deleteDocument(
            id = novelId,
            target = ElasticSearchDataSource.wenkuNovelIndexName,
        )
    }

    private suspend fun syncEs(
        novelId: String,
        title: String,
        titleZh: String,
        cover: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
    ) {
        es.client.indexDocument(
            id = novelId,
            target = ElasticSearchDataSource.wenkuNovelIndexName,
            document = WenkuNovelMetadataEsModel(
                id = novelId,
                title = title,
                titleZh = titleZh,
                cover = cover,
                authors = authors,
                artists = artists,
                keywords = keywords,
                updateAt = Clock.System.now().epochSeconds,
            ),
            refresh = Refresh.WaitFor,
        )
    }

    suspend fun updateGlossary(
        novelId: String,
        glossary: Map<String, String>,
    ) {
        mongo
            .wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                combine(
                    setValue(WebNovelMetadata::glossaryUuid, UUID.randomUUID().toString()),
                    setValue(WebNovelMetadata::glossary, glossary)
                ),
            )
    }

    suspend fun notifyUpdate(novelId: String) {
        @Serializable
        data class EsUpdate(val updateAt: Long)
        es.client.updateDocument(
            id = novelId,
            target = ElasticSearchDataSource.wenkuNovelIndexName,
            doc = EsUpdate(Clock.System.now().epochSeconds),
            refresh = Refresh.WaitFor,
        )
    }
}