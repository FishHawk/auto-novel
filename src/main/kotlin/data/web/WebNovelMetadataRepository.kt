package data.web

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import data.MongoDataSource
import data.provider.WebNovelProviderDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import util.Optional
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class WebNovelMetadataRepository(
    private val provider: WebNovelProviderDataSource,
    private val mongo: MongoDataSource,
    private val indexRepo: WebNovelIndexRepository,
    private val tocMergeHistoryRepo: WebNovelTocMergeHistoryRepository,
) {
    private val col
        get() = mongo.database.getCollection<NovelMetadata>("metadata")

    init {
        runBlocking {
            col.ensureUniqueIndex(
                NovelMetadata::providerId,
                NovelMetadata::novelId,
            )
        }
    }

    companion object {
        private fun byId(providerId: String, novelId: String): Bson =
            and(
                NovelMetadata::providerId eq providerId,
                NovelMetadata::novelId eq novelId,
            )
    }

    // List operations
    data class BookRankItem(
        val novelId: String,
        val titleJp: String,
        val titleZh: String?,
        val extra: String,
    )

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<List<BookRankItem>> {
        @Serializable
        data class NovelIdWithTitleZh(
            @SerialName("bookId")
            val novelId: String,
            val titleZh: String?,
        )

        return provider.getRank(providerId, options).map { items ->
            val idToTitleZh = col
                .withDocumentClass<NovelIdWithTitleZh>()
                .find(
                    NovelMetadata::providerId eq providerId,
                    NovelMetadata::novelId `in` items.map { it.novelId },
                )
                .toList()
                .associate { it.novelId to it.titleZh }
            items.map {
                BookRankItem(
                    novelId = it.novelId,
                    titleJp = it.title,
                    titleZh = idToTitleZh[it.novelId],
                    extra = it.meta,
                )
            }
        }
    }

    // Element operations
    @Serializable
    data class NovelMetadata(
        val providerId: String,
        @SerialName("bookId")
        val novelId: String,
        val wenkuId: String? = null,
        val titleJp: String,
        val titleZh: String?,
        val authors: List<Author>,
        val introductionJp: String,
        val introductionZh: String?,
        val glossaryUuid: String? = null,
        val glossary: Map<String, String> = emptyMap(),
        val toc: List<TocItem>,
        val visited: Long,
        val pauseUpdate: Boolean = false,
        @Contextual val syncAt: LocalDateTime,
        @Contextual val changeAt: LocalDateTime,
    ) {
        @Serializable
        data class Author(val name: String, val link: String?)

        @Serializable
        data class TocItem(
            val titleJp: String,
            val titleZh: String?,
            @SerialName("episodeId")
            val chapterId: String?
        )
    }

    suspend fun exist(providerId: String, novelId: String): Boolean =
        col.countDocuments(byId(providerId, novelId), CountOptions().limit(1)) != 0L

    suspend fun increaseVisited(providerId: String, novelId: String) =
        col.updateOne(byId(providerId, novelId), inc(NovelMetadata::visited, 1))

    suspend fun findOne(providerId: String, novelId: String): NovelMetadata? =
        col.findOne(byId(providerId, novelId))

    private suspend fun fetchRemote(providerId: String, novelId: String): Result<NovelMetadata> {
        return provider
            .getMetadata(providerId, novelId)
            .map { s ->
                NovelMetadata(
                    providerId = providerId,
                    novelId = novelId,
                    titleJp = s.title,
                    titleZh = null,
                    authors = s.authors.map { NovelMetadata.Author(it.name, it.link) },
                    introductionJp = s.introduction,
                    introductionZh = null,
                    toc = s.toc.map { NovelMetadata.TocItem(it.title, null, it.chapterId) },
                    visited = 0,
                    syncAt = LocalDateTime.now(),
                    changeAt = LocalDateTime.now(),
                )
            }
    }

    suspend fun fineOneOrFetchRemote(providerId: String, novelId: String): Result<NovelMetadata> {
        // 不在数据库中
        val metadataLocal = findOne(providerId, novelId)
            ?: return fetchRemote(providerId, novelId)
                .onSuccess {
                    col.insertOne(it)
                    syncEs(it, true)
                }

        // 在数据库中，暂停更新
        if (metadataLocal.pauseUpdate) {
            return Result.success(metadataLocal)
        }

        // 在数据库中，没有过期
        val hours = ChronoUnit.HOURS.between(metadataLocal.syncAt, LocalDateTime.now())
        val isExpired = hours > 20
        if (!isExpired) {
            return Result.success(metadataLocal)
        }

        // 在数据库中，过期，合并
        return fetchRemote(providerId, novelId).map { metadataRemote ->
            mergeRemoteMetadataToLocal(
                providerId = providerId,
                novelId = novelId,
                metadataLocal = metadataLocal,
                metadataRemote = metadataRemote,
            )!!
        }.recover {
            it.printStackTrace()
            metadataLocal
        }
    }

    private suspend fun mergeRemoteMetadataToLocal(
        providerId: String,
        novelId: String,
        metadataLocal: NovelMetadata,
        metadataRemote: NovelMetadata,
    ): NovelMetadata? {
        val list = mutableListOf<Bson>()
        if (metadataRemote.titleJp != metadataLocal.titleJp) {
            list.add(setValue(NovelMetadata::titleJp, metadataRemote.titleJp))
        }
        if (metadataRemote.introductionJp != metadataLocal.introductionJp) {
            list.add(setValue(NovelMetadata::introductionJp, metadataRemote.introductionJp))
        }

        val merged = mergeToc(
            remoteToc = metadataRemote.toc,
            localToc = metadataLocal.toc,
            isIdUnstable = isProviderIdUnstable(providerId)
        )
        list.add(setValue(NovelMetadata::toc, merged.toc))

        if (merged.hasChanged) {
            list.add(setValue(NovelMetadata::changeAt, LocalDateTime.now()))
        }
        if (merged.reviewReason != null) {
            tocMergeHistoryRepo.insertOne(
                providerId = providerId,
                novelId = novelId,
                tocOld = metadataLocal.toc,
                tocNew = metadataRemote.toc,
                reason = merged.reviewReason,
            )
        }
        list.add(setValue(NovelMetadata::syncAt, LocalDateTime.now()))

        return col.findOneAndUpdate(
            byId(providerId, novelId),
            combine(list),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.also { syncEs(it, merged.hasChanged) }
    }

    suspend fun updateWenkuId(
        providerId: String,
        novelId: String,
        wenkuId: String?,
    ): NovelMetadata? {
        return col.findOneAndUpdate(
            byId(providerId, novelId),
            setValue(NovelMetadata::wenkuId, wenkuId),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.also { syncEs(it, false) }
    }

    suspend fun updateZh(
        providerId: String,
        novelId: String,
        titleZh: Optional<String?>,
        introductionZh: Optional<String?>,
        glossary: Optional<Map<String, String>>,
        tocZh: Map<Int, String?>,
    ): NovelMetadata? {
        val list = mutableListOf<Bson>()
        titleZh.ifSome {
            list.add(setValue(NovelMetadata::titleZh, it))
        }
        introductionZh.ifSome {
            list.add(setValue(NovelMetadata::introductionZh, it))
        }
        glossary.ifSome {
            list.add(setValue(NovelMetadata::glossaryUuid, UUID.randomUUID().toString()))
            list.add(setValue(NovelMetadata::glossary, it))
        }
        tocZh.forEach { (index, itemTitleZh) ->
            list.add(setValue(NovelMetadata::toc.pos(index) / NovelMetadata.TocItem::titleZh, itemTitleZh))
        }
        list.add(setValue(NovelMetadata::changeAt, LocalDateTime.now()))

        return col.findOneAndUpdate(
            byId(providerId, novelId),
            combine(list),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.also { syncEs(it, false) }
    }

    suspend fun updateChangeAt(providerId: String, novelId: String) {
        col.findOneAndUpdate(
            byId(providerId, novelId),
            setValue(NovelMetadata::changeAt, LocalDateTime.now()),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )
    }

    private suspend fun syncEs(
        metadata: NovelMetadata,
        hasChange: Boolean,
    ) {
        if (hasChange) {
            indexRepo.index(
                metadata.providerId,
                metadata.novelId,
                metadata.titleJp,
                metadata.titleZh,
                metadata.authors.map { it.name },
                LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
            )
        } else {
            indexRepo.update(
                metadata.providerId,
                metadata.novelId,
                metadata.titleJp,
                metadata.titleZh,
                metadata.authors.map { it.name },
            )
        }
    }
}