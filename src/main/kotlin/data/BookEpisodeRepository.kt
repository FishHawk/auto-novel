package data

import data.provider.ProviderDataSource
import data.provider.SBookEpisode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.and
import org.litote.kmongo.coroutine.abortTransactionAndAwait
import org.litote.kmongo.coroutine.commitTransactionAndAwait
import org.litote.kmongo.eq
import org.litote.kmongo.ne
import org.litote.kmongo.setValue

@Serializable
data class BookEpisode(
    val providerId: String,
    val bookId: String,
    val episodeId: String,
    val paragraphsJp: List<String>,
    val paragraphsZh: List<String>?,
)

private fun SBookEpisode.toDb(providerId: String, bookId: String, episodeId: String) =
    BookEpisode(
        providerId = providerId,
        bookId = bookId,
        episodeId = episodeId,
        paragraphsJp = paragraphs,
        paragraphsZh = null,
    )


class BookEpisodeRepository(
    private val providerDataSource: ProviderDataSource,
    private val mongoDataSource: MongoDataSource,
    private val metadataRepository: BookMetadataRepository,
) {
    private val col
        get() = mongoDataSource.database.getCollection<BookEpisode>("episode")

    init {
        runBlocking {
            col.ensureUniqueIndex(
                BookEpisode::providerId,
                BookEpisode::bookId,
                BookEpisode::episodeId,
            )
        }
    }

    suspend fun countJp(providerId: String, bookId: String): Long {
        return col.countDocuments(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
            )
        )
    }

    suspend fun countZh(providerId: String, bookId: String): Long {
        return col.countDocuments(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
                BookEpisode::paragraphsZh ne null,
            )
        )
    }

    // Element operations
    private fun bsonSpecifyEpisode(providerId: String, bookId: String, episodeId: String): Bson {
        return and(
            BookEpisode::providerId eq providerId,
            BookEpisode::bookId eq bookId,
            BookEpisode::episodeId eq episodeId,
        )
    }

    suspend fun getLocal(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): BookEpisode? {
        return col.findOne(
            bsonSpecifyEpisode(providerId, bookId, episodeId)
        )
    }

    private suspend fun getEpisodeRemote(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): Result<BookEpisode> {
        return providerDataSource
            .getEpisode(providerId, bookId, episodeId)
            .map { it.toDb(providerId, bookId, episodeId) }
    }

    suspend fun get(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): Result<BookEpisode> {
        val episodeLocal = getLocal(providerId, bookId, episodeId)
        if (episodeLocal != null) return Result.success(episodeLocal)

        return getEpisodeRemote(providerId, bookId, episodeId)
            .onSuccess {
                metadataRepository.updateChangeAt(providerId, bookId)
                col.insertOne(it)
            }
    }

    suspend fun updateZh(
        providerId: String,
        bookId: String,
        episodeId: String,
        paragraphsZh: List<String>,
    ) {
        val episode = col.findOne(
            bsonSpecifyEpisode(providerId, bookId, episodeId),
        )

        if (episode == null ||
            episode.paragraphsZh != null ||
            episode.paragraphsJp.size != paragraphsZh.size
        ) {
            return
        }

        col.updateOne(
            bsonSpecifyEpisode(providerId, bookId, episodeId),
            setValue(BookEpisode::paragraphsZh, paragraphsZh)
        )

        metadataRepository.updateChangeAt(providerId, bookId)
    }
}