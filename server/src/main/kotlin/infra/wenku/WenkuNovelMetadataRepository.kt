package infra.wenku

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import infra.*
import infra.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bson.types.ObjectId
import org.litote.kmongo.combine
import org.litote.kmongo.inc
import org.litote.kmongo.setValue
import java.util.*

object WenkuNovelFilter {
    enum class Level { 全部, 一般向, R18 }
}

class WenkuNovelMetadataRepository(
    private val mongo: DataSourceMongo,
    private val es: DataSourceElasticSearch,
    private val redis: DataSourceRedis,
) {
    suspend fun search(
        userQuery: String?,
        page: Int,
        pageSize: Int,
        filterLevel: WenkuNovelFilter.Level,
    ): Page<WenkuNovelMetadataOutline> {
        val response = es.client.search(
            DataSourceElasticSearch.wenkuNovelIndexName,
            from = page * pageSize,
            size = pageSize
        ) {
            query = bool {
                val mustQueries = mutableListOf<ESQuery>()
                val mustNotQueries = mutableListOf<ESQuery>()

                // Filter level
                when (filterLevel) {
                    WenkuNovelFilter.Level.全部 -> null
                    WenkuNovelFilter.Level.一般向 -> false
                    WenkuNovelFilter.Level.R18 -> true
                }?.let {
                    mustQueries.add(
                        ESQuery(
                            "term",
                            JsonDsl().apply { put("r18", it) },
                        )
                    )
                }

                filter(mustQueries)
                mustNot(mustNotQueries)

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
        return Page(
            items = items,
            total = total,
            pageSize = pageSize,
        )
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
        keywords: List<String>,
        volumes: List<WenkuNovelVolume>,
    ): String {
        val model = WenkuNovelMetadata(
            id = ObjectId(),
            title = title,
            titleZh = titleZh,
            cover = cover,
            authors = authors,
            artists = artists,
            introduction = introduction,
            keywords = keywords,
            r18 = r18,
            volumes = volumes,
            visited = 0,
        )
        val insertResult = mongo
            .wenkuNovelMetadataCollection
            .insertOne(model)
        val id = insertResult.insertedId!!.asObjectId().value
        syncEs(model.copy(id = id))
        return id.toHexString()
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
        keywords: List<String>,
        volumes: List<WenkuNovelVolume>,
    ) {
        mongo
            .wenkuNovelMetadataCollection
            .findOneAndUpdate(
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
                        setValue(WenkuNovelMetadata::keywords, keywords),
                        setValue(WenkuNovelMetadata::volumes, volumes)
                    )
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
            ?.let {
                syncEs(metadata = it)
            }
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
        metadata: WenkuNovelMetadata,
    ) {
        es.client.indexDocument(
            id = metadata.id.toHexString(),
            target = DataSourceElasticSearch.wenkuNovelIndexName,
            document = WenkuNovelMetadataEsModel(
                id = metadata.id.toHexString(),
                title = metadata.title,
                titleZh = metadata.titleZh,
                cover = metadata.cover,
                authors = metadata.authors,
                artists = metadata.artists,
                keywords = metadata.keywords,
                r18 = metadata.r18,
                updateAt = metadata.updateAt.epochSeconds,
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
        val updateAt = Clock.System.now()
        mongo
            .wenkuNovelMetadataCollection
            .findOneAndUpdate(
                WenkuNovelMetadata.byId(novelId),
                setValue(WenkuNovelMetadata::updateAt, updateAt),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        es.client.updateDocument(
            id = novelId,
            target = DataSourceElasticSearch.wenkuNovelIndexName,
            doc = buildJsonObject {
                put("updateAt", updateAt.epochSeconds)
            },
            refresh = Refresh.WaitFor,
        )
    }
}