package infra.wenku

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.MatchOperator
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.simpleQueryString
import com.jillesvangurp.searchdsls.querydsl.sort
import com.mongodb.client.model.CountOptions
import infra.*
import infra.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.litote.kmongo.combine
import org.litote.kmongo.inc
import org.litote.kmongo.setValue
import java.util.*

class WenkuNovelMetadataRepository(
    private val mongo: DataSourceMongo,
    private val es: DataSourceElasticSearch,
    private val redis: DataSourceRedis,
) {
    suspend fun search(
        userQuery: String?,
        page: Int,
        pageSize: Int,
    ): Page<WenkuNovelMetadataOutline> {
        val response = es.client.search(
            DataSourceElasticSearch.wenkuNovelIndexName,
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

    suspend fun increaseVisited(
        userIdOrIp: String,
        novelId: String,
    ) = redis.withRateLimit("wenku-visited:${userIdOrIp}:${novelId}") {
        mongo
            .wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                inc(WenkuNovelMetadata::visited, 1),
            )
    }

    suspend fun create(
        title: String,
        titleZh: String,
        cover: String,
        authors: List<String>,
        artists: List<String>,
        r18: Boolean,
        introduction: String,
        volumes: List<WenkuNovelVolume>,
    ): String {
        val insertResult = mongo
            .wenkuNovelMetadataCollection
            .insertOne(
                WenkuNovelMetadata(
                    id = ObjectId(),
                    title = title,
                    titleZh = titleZh,
                    cover = cover,
                    authors = authors,
                    artists = artists,
                    keywords = emptyList(),
                    introduction = introduction,
                    r18 = r18,
                    volumes = volumes,
                    visited = 0,
                )
            )
        val id = insertResult.insertedId!!.asObjectId().value.toHexString()
        syncEs(
            novelId = id,
            title = title,
            titleZh = titleZh,
            cover = cover,
            authors = authors,
            artists = artists,
        )
        return id
    }

    suspend fun update(
        novelId: String,
        title: String,
        titleZh: String,
        cover: String,
        authors: List<String>,
        artists: List<String>,
        r18: Boolean,
        introduction: String,
        volumes: List<WenkuNovelVolume>,
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
                        setValue(WenkuNovelMetadata::authors, authors),
                        setValue(WenkuNovelMetadata::artists, artists),
                        setValue(WenkuNovelMetadata::r18, r18),
                        setValue(WenkuNovelMetadata::introduction, introduction),
                        setValue(WenkuNovelMetadata::volumes, volumes)
                    )
                )
            )
        syncEs(
            novelId = novelId,
            title = title,
            titleZh = titleZh,
            cover = cover,
            authors = authors,
            artists = artists,
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
            target = DataSourceElasticSearch.wenkuNovelIndexName,
        )
    }

    private suspend fun syncEs(
        novelId: String,
        title: String,
        titleZh: String,
        cover: String,
        authors: List<String>,
        artists: List<String>,
    ) {
        es.client.indexDocument(
            id = novelId,
            target = DataSourceElasticSearch.wenkuNovelIndexName,
            document = WenkuNovelMetadataEsModel(
                id = novelId,
                title = title,
                titleZh = titleZh,
                cover = cover,
                authors = authors,
                artists = artists,
                keywords = emptyList(),
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
            target = DataSourceElasticSearch.wenkuNovelIndexName,
            doc = EsUpdate(Clock.System.now().epochSeconds),
            refresh = Refresh.WaitFor,
        )
    }
}