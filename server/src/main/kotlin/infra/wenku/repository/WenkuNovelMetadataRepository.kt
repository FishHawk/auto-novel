package infra.wenku.repository

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates.*
import infra.*
import infra.common.Page
import infra.web.WebNovel
import infra.wenku.*
import infra.wenku.datasource.WenkuNovelEsDataSource
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bson.types.ObjectId
import java.util.*

class WenkuNovelMetadataRepository(
    mongo: MongoClient,
    private val es: WenkuNovelEsDataSource,
    private val redis: RedisClient,
) {
    private val wenkuNovelMetadataCollection =
        mongo.database.getCollection<WenkuNovelMetadata>(
            MongoCollectionNames.WENKU_NOVEL,
        )

    suspend fun search(
        userQuery: String?,
        page: Int,
        pageSize: Int,
        filterLevel: WenkuNovelFilter.Level,
    ): Page<WenkuNovelMetadataListItem> {
        val (items, total) = es.searchNovel(
            userQuery = userQuery,
            page = page,
            pageSize = pageSize,
            filterLevel = filterLevel,
        )
        return Page(
            items = items.map {
                WenkuNovelMetadataListItem(
                    id = it.id,
                    title = it.title,
                    titleZh = it.titleZh,
                    cover = it.cover,
                )
            },
            total = total,
            pageSize = pageSize,
        )
    }

    suspend fun exist(novelId: String): Boolean {
        return wenkuNovelMetadataCollection
            .countDocuments(
                WenkuNovelMetadata.byId(novelId),
                CountOptions().limit(1),
            ) > 0L
    }

    suspend fun get(novelId: String): WenkuNovelMetadata? {
        return wenkuNovelMetadataCollection
            .find(WenkuNovelMetadata.byId(novelId))
            .firstOrNull()
    }

    suspend fun increaseVisited(
        userIdOrIp: String,
        novelId: String,
    ) = redis.withRateLimit("wenku-visited:${userIdOrIp}:${novelId}") {
        wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                inc(WenkuNovelMetadata::visited.field(), 1),
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
        val insertResult = wenkuNovelMetadataCollection
            .insertOne(model)
        val id = insertResult.insertedId!!.asObjectId().value
        es.syncNovel(model.copy(id = id))
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
        wenkuNovelMetadataCollection
            .findOneAndUpdate(
                WenkuNovelMetadata.byId(novelId),
                combine(
                    listOf(
                        set(WenkuNovelMetadata::title.field(), title),
                        set(WenkuNovelMetadata::titleZh.field(), titleZh),
                        set(WenkuNovelMetadata::cover.field(), cover),
                        set(WenkuNovelMetadata::authors.field(), authors),
                        set(WenkuNovelMetadata::artists.field(), artists),
                        set(WenkuNovelMetadata::publisher.field(), volumes.firstNotNullOfOrNull { it.publisher }),
                        set(WenkuNovelMetadata::imprint.field(), volumes.firstNotNullOfOrNull { it.imprint }),
                        set(
                            WenkuNovelMetadata::latestPublishAt.field(),
                            volumes.mapNotNull { it.publishAt }.maxOrNull()?.let { Instant.fromEpochSeconds(it) },
                        ),
                        set(WenkuNovelMetadata::level.field(), level),
                        set(WenkuNovelMetadata::introduction.field(), introduction),
                        set(WenkuNovelMetadata::keywords.field(), keywords),
                        set(WenkuNovelMetadata::volumes.field(), volumes)
                    )
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
            ?.let {
                es.syncNovel(it)
            }
    }

    suspend fun deleteNovel(
        novelId: String,
    ) {
        wenkuNovelMetadataCollection
            .deleteOne(
                eq(WenkuNovelMetadata::id.field(), ObjectId(novelId)),
            )
        es.deleteNovel(novelId)
    }

    suspend fun updateGlossary(
        novelId: String,
        glossary: Map<String, String>,
    ) {
        wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                combine(
                    set(WebNovel::glossaryUuid.field(), UUID.randomUUID().toString()),
                    set(WebNovel::glossary.field(), glossary)
                ),
            )
    }

    suspend fun addWebId(
        novelId: String,
        webId: String,
    ) {
        wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                addToSet(WenkuNovelMetadata::webIds.field(), webId),
            )
    }

    suspend fun removeWebId(
        novelId: String,
        webId: String,
    ) {
        wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                pull(WenkuNovelMetadata::webIds.field(), webId),
            )
    }

    suspend fun notifyUpdate(novelId: String) {
        val updateAt = Clock.System.now()
        val novel = wenkuNovelMetadataCollection
            .findOneAndUpdate(
                WenkuNovelMetadata.byId(novelId),
                set(WenkuNovelMetadata::updateAt.field(), updateAt),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )!!
        es.syncNovel(novel)
    }
}
