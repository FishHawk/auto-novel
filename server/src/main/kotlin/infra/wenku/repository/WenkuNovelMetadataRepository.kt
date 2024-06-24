package infra.wenku.repository

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates.*
import infra.*
import infra.common.Page
import infra.web.WebNovel
import infra.wenku.*
import infra.wenku.datasource.WenkuNovelEsDataSource
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
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
        mongo.database.getCollection<WenkuNovel>(
            MongoCollectionNames.WENKU_NOVEL,
        )
    private val userFavoredWenkuCollection =
        mongo.database.getCollection<WenkuNovelFavoriteDbModel>(
            MongoCollectionNames.WENKU_FAVORITE,
        )

    suspend fun search(
        userId: String?,
        userQuery: String?,
        page: Int,
        pageSize: Int,
        filterLevel: WenkuNovelFilter.Level,
    ): Page<WenkuNovelListItem> {
        val (items, total) = es.searchNovel(
            userQuery = userQuery,
            page = page,
            pageSize = pageSize,
            filterLevel = filterLevel,
        )
        val ids = items.map { ObjectId(it.id) }
        val favoredList = userId?.let {
            userFavoredWenkuCollection
                .find(
                    and(
                        eq(WenkuNovelFavoriteDbModel::userId.field(), ObjectId(it)),
                        `in`(WenkuNovelFavoriteDbModel::novelId.field(), ids),
                    )
                )
                .toList()
        }
        return Page(
            items = items.map { novel ->
                val favored = favoredList?.find { it.novelId.toHexString() == novel.id }
                WenkuNovelListItem(
                    id = novel.id,
                    title = novel.title,
                    titleZh = novel.titleZh,
                    cover = novel.cover,
                    favored = favored?.favoredId,
                )
            },
            total = total,
            pageSize = pageSize,
        )
    }

    suspend fun exist(novelId: String): Boolean {
        return wenkuNovelMetadataCollection
            .countDocuments(
                WenkuNovel.byId(novelId),
                CountOptions().limit(1),
            ) > 0L
    }

    suspend fun get(novelId: String): WenkuNovel? {
        return wenkuNovelMetadataCollection
            .find(WenkuNovel.byId(novelId))
            .firstOrNull()
    }

    suspend fun increaseVisited(
        userIdOrIp: String,
        novelId: String,
    ) = redis.withRateLimit("wenku-visited:${userIdOrIp}:${novelId}") {
        wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovel.byId(novelId),
                inc(WenkuNovel::visited.field(), 1),
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
        val model = WenkuNovel(
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
                WenkuNovel.byId(novelId),
                combine(
                    listOf(
                        set(WenkuNovel::title.field(), title),
                        set(WenkuNovel::titleZh.field(), titleZh),
                        set(WenkuNovel::cover.field(), cover),
                        set(WenkuNovel::authors.field(), authors),
                        set(WenkuNovel::artists.field(), artists),
                        set(WenkuNovel::publisher.field(), volumes.firstNotNullOfOrNull { it.publisher }),
                        set(WenkuNovel::imprint.field(), volumes.firstNotNullOfOrNull { it.imprint }),
                        set(
                            WenkuNovel::latestPublishAt.field(),
                            volumes.mapNotNull { it.publishAt }.maxOrNull()?.let { Instant.fromEpochSeconds(it) },
                        ),
                        set(WenkuNovel::level.field(), level),
                        set(WenkuNovel::introduction.field(), introduction),
                        set(WenkuNovel::keywords.field(), keywords),
                        set(WenkuNovel::volumes.field(), volumes)
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
                eq(WenkuNovel::id.field(), ObjectId(novelId)),
            )
        es.deleteNovel(novelId)
    }

    suspend fun updateGlossary(
        novelId: String,
        glossary: Map<String, String>,
    ) {
        wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovel.byId(novelId),
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
                WenkuNovel.byId(novelId),
                addToSet(WenkuNovel::webIds.field(), webId),
            )
    }

    suspend fun removeWebId(
        novelId: String,
        webId: String,
    ) {
        wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovel.byId(novelId),
                pull(WenkuNovel::webIds.field(), webId),
            )
    }

    suspend fun notifyUpdate(novelId: String) {
        val updateAt = Clock.System.now()
        val novel = wenkuNovelMetadataCollection
            .findOneAndUpdate(
                WenkuNovel.byId(novelId),
                set(WenkuNovel::updateAt.field(), updateAt),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )!!
        es.syncNovel(novel)
    }
}
