package data.web

import com.mongodb.client.model.Updates
import data.MongoDataSource
import data.provider.ProviderDataSource
import data.provider.SBookEpisode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*

@Serializable
data class BookEpisode(
    val providerId: String,
    val bookId: String,
    val episodeId: String,
    @SerialName("paragraphsJp")
    val paragraphs: List<String>,

    @SerialName("glossaryUuid")
    val baiduGlossaryUuid: String? = null,
    @SerialName("glossary")
    val baiduGlossary: Map<String, String> = emptyMap(),
    @SerialName("paragraphsZh")
    val baiduParagraphs: List<String>?,

    val youdaoGlossaryUuid: String? = null,
    val youdaoGlossary: Map<String, String> = emptyMap(),
    val youdaoParagraphs: List<String>? = null,
)

private fun SBookEpisode.toDb(providerId: String, bookId: String, episodeId: String) =
    BookEpisode(
        providerId = providerId,
        bookId = bookId,
        episodeId = episodeId,
        paragraphs = paragraphs,
        baiduParagraphs = null,
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

    suspend fun count(providerId: String, bookId: String): Long {
        return col.countDocuments(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
            )
        )
    }

    suspend fun countBaidu(providerId: String, bookId: String): Long {
        return col.countDocuments(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
                BookEpisode::baiduParagraphs ne null,
            )
        )
    }

    suspend fun countYoudao(providerId: String, bookId: String): Long {
        return col.countDocuments(
            and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
                BookEpisode::youdaoParagraphs ne null,
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

    private suspend fun getRemote(
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

        return getRemote(providerId, bookId, episodeId)
            .onSuccess {
                metadataRepository.updateChangeAt(providerId, bookId)
                col.insertOne(it)
            }
    }

    suspend fun delete(
        providerId: String,
        bookId: String,
        episodeId: String,
    ) {
        col.deleteOne(bsonSpecifyEpisode(providerId, bookId, episodeId))
    }

    suspend fun updateBaidu(
        providerId: String,
        bookId: String,
        episodeId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: List<String>,
    ) {
        col.updateOne(
            bsonSpecifyEpisode(providerId, bookId, episodeId),
            combine(
                setValue(BookEpisode::baiduGlossaryUuid, glossaryUuid),
                setValue(BookEpisode::baiduGlossary, glossary),
                setValue(BookEpisode::baiduParagraphs, paragraphsZh)
            )
        )
        metadataRepository.updateChangeAt(providerId, bookId)
    }

    suspend fun updateBaidu(
        providerId: String,
        bookId: String,
        episodeId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: Map<Int, String>,
    ) {
        col.updateOne(
            bsonSpecifyEpisode(providerId, bookId, episodeId),
            combine(
                listOf(
                    setValue(BookEpisode::baiduGlossaryUuid, glossaryUuid),
                    setValue(BookEpisode::baiduGlossary, glossary),
                ) + paragraphsZh.map { (index, textZh) ->
                    // hacky, fix https://github.com/Litote/kmongo/issues/415
                    Updates.set("paragraphsZh.${index}", textZh)
                }
            ),
        )
        metadataRepository.updateChangeAt(providerId, bookId)
    }

    suspend fun updateYoudao(
        providerId: String,
        bookId: String,
        episodeId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: List<String>,
    ) {
        col.updateOne(
            bsonSpecifyEpisode(providerId, bookId, episodeId),
            combine(
                setValue(BookEpisode::youdaoGlossaryUuid, glossaryUuid),
                setValue(BookEpisode::youdaoGlossary, glossary),
                setValue(BookEpisode::youdaoParagraphs, paragraphsZh)
            )
        )
        metadataRepository.updateChangeAt(providerId, bookId)
    }

    suspend fun updateYoudao(
        providerId: String,
        bookId: String,
        episodeId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: Map<Int, String>,
    ) {
        col.updateOne(
            bsonSpecifyEpisode(providerId, bookId, episodeId),
            combine(
                listOf(
                    setValue(BookEpisode::youdaoGlossaryUuid, glossaryUuid),
                    setValue(BookEpisode::youdaoGlossary, glossary),
                ) + paragraphsZh.map { (index, textZh) ->
                    setValue(BookEpisode::youdaoParagraphs.pos(index), textZh)
                }
            ),
        )
        metadataRepository.updateChangeAt(providerId, bookId)
    }

//    suspend fun updateBaidu(
//        providerId: String,
//        bookId: String,
//        episodeId: String,
//        paragraphsZh: Map<Int, String>,
//    ) {
//        col.updateOne(
//            bsonSpecifyEpisode(providerId, bookId, episodeId),
//            combine(
//                paragraphsZh.map { (index, textZh) ->
//                    setValue(BookEpisode::baiduParagraphs.pos(index), textZh)
//                }
//            ),
//        )
//        metadataRepository.updateChangeAt(providerId, bookId)
//    }
}