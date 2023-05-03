package data.web

import com.mongodb.client.model.Updates
import data.MongoDataSource
import data.provider.ProviderDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*

class WebBookEpisodeRepository(
    private val provider: ProviderDataSource,
    private val mongo: MongoDataSource,
    private val metadataRepository: WebBookMetadataRepository,
) {
    private val col
        get() = mongo.database.getCollection<BookEpisode>("episode")

    companion object {
        private fun byId(providerId: String, bookId: String, episodeId: String): Bson {
            return and(
                BookEpisode::providerId eq providerId,
                BookEpisode::bookId eq bookId,
                BookEpisode::episodeId eq episodeId,
            )
        }
    }

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
    suspend fun getLocal(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): BookEpisode? {
        return col.findOne(
            byId(providerId, bookId, episodeId)
        )
    }

    private suspend fun getRemote(
        providerId: String,
        bookId: String,
        episodeId: String,
    ): Result<BookEpisode> {
        return provider
            .getEpisode(providerId, bookId, episodeId)
            .map {
                BookEpisode(
                    providerId = providerId,
                    bookId = bookId,
                    episodeId = episodeId,
                    paragraphs = it.paragraphs,
                    baiduParagraphs = null,
                )
            }
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

    suspend fun deleteOne(
        providerId: String,
        bookId: String,
        episodeId: String,
    ) {
        col.deleteOne(byId(providerId, bookId, episodeId))
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
            byId(providerId, bookId, episodeId),
            combine(
                setValue(BookEpisode::baiduGlossaryUuid, glossaryUuid),
                setValue(BookEpisode::baiduGlossary, glossary),
                setValue(BookEpisode::baiduParagraphs, paragraphsZh)
            )
        )
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
            byId(providerId, bookId, episodeId),
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
            byId(providerId, bookId, episodeId),
            combine(
                setValue(BookEpisode::youdaoGlossaryUuid, glossaryUuid),
                setValue(BookEpisode::youdaoGlossary, glossary),
                setValue(BookEpisode::youdaoParagraphs, paragraphsZh)
            )
        )
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
            byId(providerId, bookId, episodeId),
            combine(
                listOf(
                    setValue(BookEpisode::youdaoGlossaryUuid, glossaryUuid),
                    setValue(BookEpisode::youdaoGlossary, glossary),
                ) + paragraphsZh.map { (index, textZh) ->
                    setValue(BookEpisode::youdaoParagraphs.pos(index), textZh)
                }
            ),
        )
    }
}