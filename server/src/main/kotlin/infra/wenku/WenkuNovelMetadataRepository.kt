package infra.wenku

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import domain.entity.*
import infra.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bson.types.ObjectId
import org.litote.kmongo.*
import util.serialName
import java.util.*

object WenkuNovelFilter {
    enum class Level { 全部, 一般向, 成人向, 严肃向 }
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
                    WenkuNovelFilter.Level.一般向 -> WenkuNovelLevel.一般向
                    WenkuNovelFilter.Level.成人向 -> WenkuNovelLevel.成人向
                    WenkuNovelFilter.Level.严肃向 -> WenkuNovelLevel.严肃向
                }?.let {
                    mustQueries.add(term(WenkuNovelMetadataEsModel::level, it.serialName()))
                }

                // Parse query
                val queryWords = mutableListOf<String>()
                userQuery
                    ?.split(" ")
                    ?.forEach { token ->
                        if (token.endsWith('$')) {
                            val rawToken = token.removePrefix("-").removeSuffix("$")
                            val queries =
                                if (token.startsWith("-")) mustNotQueries
                                else mustQueries
                            val field = WenkuNovelMetadataEsModel::keywords
                            queries.add(term(field, rawToken))
                        } else {
                            queryWords.add(token)
                        }
                    }

                filter(mustQueries)
                mustNot(mustNotQueries)

                if (queryWords.isNotEmpty()) {
                    must(
                        simpleQueryString(
                            queryWords.joinToString(" "),
                            WenkuNovelMetadataEsModel::title,
                            WenkuNovelMetadataEsModel::titleZh,
                            WenkuNovelMetadataEsModel::authors,
                            WenkuNovelMetadataEsModel::artists,
                            WenkuNovelMetadataEsModel::keywords,
                            WenkuNovelMetadataEsModel::publisher,
                            WenkuNovelMetadataEsModel::imprint,
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
        cover: String?,
        authors: List<String>,
        artists: List<String>,
        level: WenkuNovelLevel,
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
            publisher = volumes.firstNotNullOfOrNull { it.publisher },
            imprint = volumes.firstNotNullOfOrNull { it.imprint },
            latestPublishAt = volumes.mapNotNull { it.publishAt }.maxOrNull()
                ?.let { Instant.fromEpochSeconds(it) },
            level = level,
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
        cover: String?,
        authors: List<String>,
        artists: List<String>,
        level: WenkuNovelLevel,
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
                        setValue(WenkuNovelMetadata::publisher, volumes.firstNotNullOfOrNull { it.publisher }),
                        setValue(WenkuNovelMetadata::imprint, volumes.firstNotNullOfOrNull { it.imprint }),
                        setValue(
                            WenkuNovelMetadata::latestPublishAt,
                            volumes.mapNotNull { it.publishAt }.maxOrNull()
                                ?.let { Instant.fromEpochSeconds(it) },
                        ),
                        setValue(WenkuNovelMetadata::level, level),
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
                publisher = metadata.publisher,
                imprint = metadata.imprint,
                latestPublishAt = metadata.latestPublishAt,
                level = metadata.level,
                updateAt = metadata.updateAt,
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

    suspend fun addWebId(
        novelId: String,
        webId: String,
    ) {
        mongo
            .wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                addToSet(WenkuNovelMetadata::webIds, webId),
            )
    }

    suspend fun removeWebId(
        novelId: String,
        webId: String,
    ) {
        mongo
            .wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                pull(WenkuNovelMetadata::webIds, webId),
            )
    }

    suspend fun notifyUpdate(novelId: String) {
        val updateAt = Clock.System.now()
        val novel = mongo
            .wenkuNovelMetadataCollection
            .findOneAndUpdate(
                WenkuNovelMetadata.byId(novelId),
                setValue(WenkuNovelMetadata::updateAt, updateAt),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )!!
        syncEs(novel)
    }
}
