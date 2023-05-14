package data.web

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import data.MongoDataSource
import data.provider.ProviderDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import util.Optional
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class WebBookMetadataRepository(
    private val provider: ProviderDataSource,
    private val mongo: MongoDataSource,
    private val webBookIndexRepository: WebBookIndexRepository,
    private val tocMergeHistoryRepository: WebBookTocMergeHistoryRepository,
) {
    @Serializable
    data class BookMetadata(
        val providerId: String,
        val bookId: String,
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
        val downloaded: Long,
        val pauseUpdate: Boolean = false,
        @Contextual val syncAt: LocalDateTime,
        @Contextual val changeAt: LocalDateTime,
    ) {
        @Serializable
        data class Author(val name: String, val link: String?)

        @Serializable
        data class TocItem(val titleJp: String, val titleZh: String?, val episodeId: String?)
    }


    private val col
        get() = mongo.database.getCollection<BookMetadata>("metadata")

    init {
        runBlocking {
            col.ensureUniqueIndex(
                BookMetadata::providerId,
                BookMetadata::bookId,
            )
        }
    }

    data class BookRankItem(
        val bookId: String,
        val titleJp: String,
        val titleZh: String?,
        val extra: String,
    )

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<List<BookRankItem>> {
        @Serializable
        data class BookIdWithTitleZh(val bookId: String, val titleZh: String?)

        return provider.getRank(providerId, options).map { items ->
            val idToTitleZh = col
                .withDocumentClass<BookIdWithTitleZh>()
                .find(
                    BookMetadata::providerId eq providerId,
                    BookMetadata::bookId `in` items.map { it.bookId },
                )
                .toList()
                .associate { it.bookId to it.titleZh }
            items.map {
                BookRankItem(
                    bookId = it.bookId,
                    titleJp = it.title,
                    titleZh = idToTitleZh[it.bookId],
                    extra = it.meta,
                )
            }
        }
    }

    // Element operations
    companion object {
        private fun byId(providerId: String, bookId: String): Bson {
            return and(
                BookMetadata::providerId eq providerId,
                BookMetadata::bookId eq bookId,
            )
        }
    }

    suspend fun exist(providerId: String, bookId: String): Boolean {
        return col.countDocuments(byId(providerId, bookId), CountOptions().limit(1)) != 0L
    }

    suspend fun increaseVisited(providerId: String, bookId: String) {
        col.updateOne(
            byId(providerId, bookId),
            inc(BookMetadata::visited, 1)
        )
    }

    suspend fun increaseDownloaded(providerId: String, bookId: String) {
        col.updateOne(
            byId(providerId, bookId),
            inc(BookMetadata::downloaded, 1)
        )
    }

    suspend fun getLocal(providerId: String, bookId: String): BookMetadata? {
        return col.findOne(
            byId(providerId, bookId),
        )
    }

    private suspend fun getRemote(providerId: String, bookId: String): Result<BookMetadata> {
        return provider
            .getMetadata(providerId, bookId)
            .map { s ->
                BookMetadata(
                    providerId = providerId,
                    bookId = bookId,
                    titleJp = s.title,
                    titleZh = null,
                    authors = s.authors.map { BookMetadata.Author(it.name, it.link) },
                    introductionJp = s.introduction,
                    introductionZh = null,
                    toc = s.toc.map { BookMetadata.TocItem(it.title, null, it.episodeId) },
                    visited = 0,
                    downloaded = 0,
                    syncAt = LocalDateTime.now(),
                    changeAt = LocalDateTime.now(),
                )
            }
    }

    suspend fun get(providerId: String, bookId: String): Result<BookMetadata> {
        // 不在数据库中
        val metadataLocal = getLocal(providerId, bookId)
            ?: return getRemote(providerId, bookId)
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
        return getRemote(providerId, bookId).map { metadataRemote ->
            mergeRemoteMetadataToLocal(
                providerId = providerId,
                bookId = bookId,
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
        bookId: String,
        metadataLocal: BookMetadata,
        metadataRemote: BookMetadata,
    ): BookMetadata? {
        val list = mutableListOf<Bson>()
        if (metadataRemote.titleJp != metadataLocal.titleJp) {
            list.add(setValue(BookMetadata::titleJp, metadataRemote.titleJp))
        }
        if (metadataRemote.introductionJp != metadataLocal.introductionJp) {
            list.add(setValue(BookMetadata::introductionJp, metadataRemote.introductionJp))
        }

        val merged = mergeToc(
            remoteToc = metadataRemote.toc,
            localToc = metadataLocal.toc,
            isIdUnstable = isProviderIdUnstable(providerId)
        )
        list.add(setValue(BookMetadata::toc, merged.toc))

        if (merged.hasChanged) {
            list.add(setValue(BookMetadata::changeAt, LocalDateTime.now()))
        }
        if (merged.reviewReason != null) {
            tocMergeHistoryRepository.insert(
                providerId = providerId,
                bookId = bookId,
                tocOld = metadataLocal.toc,
                tocNew = metadataRemote.toc,
                reason = merged.reviewReason,
            )
        }
        list.add(setValue(BookMetadata::syncAt, LocalDateTime.now()))

        return col.findOneAndUpdate(
            byId(providerId, bookId),
            combine(list),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.also { syncEs(it, merged.hasChanged) }
    }

    suspend fun updateWenkuId(
        providerId: String,
        bookId: String,
        wenkuId: String?,
    ): BookMetadata? {
        return col.findOneAndUpdate(
            byId(providerId, bookId),
            setValue(BookMetadata::wenkuId, wenkuId),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.also { syncEs(it, false) }
    }

    suspend fun updateZh(
        providerId: String,
        bookId: String,
        titleZh: Optional<String?>,
        introductionZh: Optional<String?>,
        glossary: Optional<Map<String, String>>,
        tocZh: Map<Int, String?>,
    ): BookMetadata? {
        val list = mutableListOf<Bson>()
        titleZh.ifSome {
            list.add(setValue(BookMetadata::titleZh, it))
        }
        introductionZh.ifSome {
            list.add(setValue(BookMetadata::introductionZh, it))
        }
        glossary.ifSome {
            list.add(setValue(BookMetadata::glossaryUuid, UUID.randomUUID().toString()))
            list.add(setValue(BookMetadata::glossary, it))
        }
        tocZh.forEach { (index, itemTitleZh) ->
            list.add(setValue(BookMetadata::toc.pos(index) / BookMetadata.TocItem::titleZh, itemTitleZh))
        }
        list.add(setValue(BookMetadata::changeAt, LocalDateTime.now()))

        return col.findOneAndUpdate(
            byId(providerId, bookId),
            combine(list),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )?.also { syncEs(it, false) }
    }

    suspend fun updateChangeAt(providerId: String, bookId: String) {
        col.findOneAndUpdate(
            byId(providerId, bookId),
            setValue(BookMetadata::changeAt, LocalDateTime.now()),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )
    }

    private suspend fun syncEs(
        metadata: BookMetadata,
        hasChange: Boolean,
    ) {
        if (hasChange) {
            webBookIndexRepository.index(
                metadata.providerId,
                metadata.bookId,
                metadata.titleJp,
                metadata.titleZh,
                metadata.authors.map { it.name },
                LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
            )
        } else {
            webBookIndexRepository.update(
                metadata.providerId,
                metadata.bookId,
                metadata.titleJp,
                metadata.titleZh,
                metadata.authors.map { it.name },
            )
        }
    }

    suspend fun setToc(
        providerId: String,
        bookId: String,
        toc: List<BookMetadata.TocItem>,
    ) {
        col.updateOne(
            byId(providerId, bookId),
            setValue(BookMetadata::toc, toc),
        )
    }

    suspend fun setPauseUpdate(
        providerId: String,
        bookId: String,
        pauseUpdate: Boolean,
    ) {
        col.updateOne(
            byId(providerId, bookId),
            setValue(BookMetadata::pauseUpdate, pauseUpdate),
        )
    }
}