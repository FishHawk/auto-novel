package data

import data.provider.ProviderRegister
import data.provider.SBookEpisode
import data.provider.SBookMetadata
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun createDatabase(): CoroutineDatabase {
    val client = KMongo.createClient("mongodb://localhost:27017").coroutine
    return client.getDatabase("main")
}

@Serializable
data class BookAuthor(
    val name: String,
    val link: String?,
)

@Serializable
data class BookTocItem(
    val titleJp: String,
    val titleZh: String?,
    val episodeId: String?,
)

@Serializable
data class BookMetadata(
    val providerId: String,
    val bookId: String,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<BookAuthor>,
    val introductionJp: String,
    val introductionZh: String?,
    val toc: List<BookTocItem>,
    val visited: Long,
    val downloaded: Long,
    @Contextual val syncAt: LocalDateTime,
    @Contextual val changeAt: LocalDateTime,
)

@Serializable
data class BookEpisode(
    val providerId: String,
    val bookId: String,
    val episodeId: String,
    val paragraphsJp: List<String>,
    val paragraphsZh: List<String>?,
)

private fun SBookMetadata.toDb(providerId: String, bookId: String) =
    BookMetadata(
        providerId = providerId,
        bookId = bookId,
        titleJp = title,
        titleZh = null,
        authors = authors.map { BookAuthor(it.name, it.link) },
        introductionJp = introduction,
        introductionZh = null,
        toc = toc.map { BookTocItem(it.title, null, it.episodeId) },
        visited = 0,
        downloaded = 0,
        syncAt = LocalDateTime.now(),
        changeAt = LocalDateTime.now(),
    )

private fun SBookEpisode.toDb(providerId: String, bookId: String, episodeId: String) =
    BookEpisode(
        providerId = providerId,
        bookId = bookId,
        episodeId = episodeId,
        paragraphsJp = paragraphs,
        paragraphsZh = null,
    )

class BookRepository(private val database: CoroutineDatabase) {
    private val metadataCol
        get() = database.getCollection<BookMetadata>("metadata")

    private val episodeCol
        get() = database.getCollection<BookEpisode>("episode")

    init {
        runBlocking {
            metadataCol.ensureUniqueIndex(
                BookMetadata::providerId,
                BookMetadata::bookId,
            )
            episodeCol.ensureUniqueIndex(
                BookEpisode::providerId,
                BookEpisode::bookId,
                BookEpisode::episodeId,
            )
        }
    }

    suspend fun countMetadata(): Long {
        return metadataCol.countDocuments()
    }

    suspend fun countEpisodeJp(providerId: String, bookId: String): Long {
        return episodeCol.countDocuments(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
            )
        )
    }

    suspend fun countEpisodeZh(providerId: String, bookId: String): Long {
        return episodeCol.countDocuments(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
                BookEpisode::paragraphsZh ne null,
            )
        )
    }

    suspend fun increaseVisited(providerId: String, bookId: String) {
        metadataCol.updateOne(
            and(
                BookMetadata::providerId eq providerId,
                BookMetadata::bookId eq bookId,
            ),
            inc(BookMetadata::visited, 1)
        )
    }

    suspend fun increaseDownloaded(providerId: String, bookId: String) {
        metadataCol.updateOne(
            and(
                BookMetadata::providerId eq providerId,
                BookMetadata::bookId eq bookId,
            ),
            inc(BookMetadata::downloaded, 1)
        )
    }

    private suspend fun getMetadataInDb(providerId: String, bookId: String): BookMetadata? {
        return metadataCol.findOne(
            BookMetadata::providerId eq providerId,
            BookMetadata::bookId eq bookId,
        )
    }

    private suspend fun getMetadataFromProvider(providerId: String, bookId: String): BookMetadata {
        return ProviderRegister
            .getProvider(providerId)!!
            .getMetadata(bookId)
            .toDb(providerId, bookId)
    }

    suspend fun getMetadata(providerId: String, bookId: String): BookMetadata {
        val metadataInDb = getMetadataInDb(providerId, bookId)
        if (metadataInDb == null) {
            val metadataFromProvider = getMetadataFromProvider(providerId, bookId)
            metadataCol.insertOne(metadataFromProvider)
            return metadataFromProvider
        } else {
            val now = LocalDateTime.now()
            val days = ChronoUnit.DAYS.between(metadataInDb.syncAt, now)
            if (days > 2) {
                val metadataFromProvider = getMetadataFromProvider(providerId, bookId)

                val list = mutableListOf<Bson>()
                if (metadataInDb.titleJp != metadataFromProvider.titleJp) {
                    list.add(setValue(BookMetadata::titleJp, metadataFromProvider.titleJp))
                    list.add(setValue(BookMetadata::titleZh, null))
                }
                if (metadataInDb.introductionJp != metadataFromProvider.introductionJp) {
                    list.add(setValue(BookMetadata::introductionJp, metadataFromProvider.introductionJp))
                    list.add(setValue(BookMetadata::introductionZh, null))
                }
                if (metadataInDb.toc.size < metadataFromProvider.toc.size) {
                    val mergedToc = metadataFromProvider.toc.mapIndexed { index, tocItemNew ->
                        val tocItemOld = metadataInDb.toc.getOrNull(index)
                        if (
                            tocItemOld?.titleZh != null &&
                            tocItemOld.titleJp == tocItemNew.titleJp &&
                            tocItemOld.episodeId == tocItemNew.episodeId
                        ) {
                            tocItemOld
                        } else {
                            tocItemNew
                        }
                    }
                    list.add(setValue(BookMetadata::toc, mergedToc))
                }
                if (list.isNotEmpty()) {
                    list.add(setValue(BookMetadata::changeAt, LocalDateTime.now()))
                }
                list.add(setValue(BookMetadata::syncAt, LocalDateTime.now()))

                return metadataCol.findOneAndUpdate(
                    and(
                        BookEpisode::providerId eq providerId,
                        BookEpisode::bookId eq bookId,
                    ),
                    combine(*list.toTypedArray()),
                )!!
            } else {
                return metadataInDb
            }
        }
    }

    suspend fun updateMetadata(
        providerId: String,
        bookId: String,
        titleZh: String?,
        introductionZh: String?,
        toc: Map<Int, String>,
    ) {
        val metadata = getMetadataInDb(providerId, bookId)!!
        val list = mutableListOf<Bson>()

        if (metadata.titleZh == null && titleZh != null) {
            list.add(setValue(BookMetadata::titleZh, titleZh))
        }
        if (metadata.introductionZh == null && introductionZh != null) {
            list.add(setValue(BookMetadata::introductionZh, introductionZh))
        }
        if (toc.isNotEmpty()) {
            val tocMerged = metadata.toc.mapIndexed { index, it ->
                val tocTitleZh = toc[index]
                if (it.titleZh == null && tocTitleZh != null) {
                    it.copy(titleZh = tocTitleZh)
                } else {
                    it
                }
            }
            list.add(setValue(BookMetadata::toc, tocMerged))
        }
        if (list.isNotEmpty()) {
            list.add(setValue(BookMetadata::changeAt, LocalDateTime.now()))
        }

        metadataCol.updateOne(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
            ),
            combine(*list.toTypedArray()),
        )
    }

    private suspend fun updateMetadataChangeAt(
        providerId: String,
        bookId: String?,
    ) {
        metadataCol.updateOne(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
            ),
            setValue(BookMetadata::changeAt, LocalDateTime.now()),
        )
    }

    suspend fun getEpisodeInDb(providerId: String, bookId: String, episodeId: String): BookEpisode? {
        return episodeCol.findOne(
            BookEpisode::providerId eq providerId,
            BookEpisode::bookId eq bookId,
            BookEpisode::episodeId eq episodeId,
        )
    }

    suspend fun getEpisode(providerId: String, bookId: String, episodeId: String): BookEpisode {
        val episodeInDb = getEpisodeInDb(providerId, bookId, episodeId)
        return if (episodeInDb == null) {
            val episodeFromSource = ProviderRegister
                .getProvider(providerId)!!
                .getEpisode(bookId, episodeId)
                .toDb(providerId, bookId, episodeId)
            episodeCol.insertOne(episodeFromSource)
            updateMetadataChangeAt(providerId, bookId)
            episodeFromSource
        } else {
            episodeInDb
        }
    }

    suspend fun updateEpisode(
        providerId: String,
        bookId: String,
        episodeId: String,
        paragraphsZh: List<String>,
    ) {
        val episode = getEpisodeInDb(providerId, bookId, episodeId)!!
        if (episode.paragraphsZh != null) return

        assert(episode.paragraphsJp.size == paragraphsZh.size)

        updateMetadataChangeAt(providerId, bookId)
        episodeCol.updateOne(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
                BookEpisode::episodeId eq episodeId,
            ),
            setValue(BookEpisode::paragraphsZh, paragraphsZh)
        )
    }

    suspend fun list(page: Int, pageSize: Int): List<BookMetadata> {
        return metadataCol.find().skip(page * pageSize).limit(pageSize).toList()
    }
}