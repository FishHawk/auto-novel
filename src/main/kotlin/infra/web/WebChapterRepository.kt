package infra.web

import com.mongodb.client.model.Updates
import infra.MongoDataSource
import infra.provider.WebNovelProviderDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*

class WebChapterRepository(
    private val provider: WebNovelProviderDataSource,
    private val mongo: MongoDataSource,
    private val metadataRepository: WebNovelMetadataRepository,
) {
    private val col
        get() = mongo.database.getCollection<NovelChapter>("episode")

    companion object {
        private fun byId(providerId: String, novelId: String, chapterId: String): Bson {
            return and(
                NovelChapter::providerId eq providerId,
                NovelChapter::novelId eq novelId,
                NovelChapter::chapterId eq chapterId,
            )
        }
    }

    @Serializable
    data class NovelChapter(
        val providerId: String,
        @SerialName("bookId")
        val novelId: String,
        @SerialName("episodeId")
        val chapterId: String,
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
                NovelChapter::providerId,
                NovelChapter::novelId,
                NovelChapter::chapterId,
            )
        }
    }

    suspend fun count(providerId: String, novelId: String): Long {
        return col.countDocuments(
            and(
                NovelChapter::providerId eq providerId,
                NovelChapter::novelId eq novelId,
            )
        )
    }

    suspend fun countBaidu(providerId: String, novelId: String): Long {
        return col.countDocuments(
            and(
                NovelChapter::providerId eq providerId,
                NovelChapter::novelId eq novelId,
                NovelChapter::baiduParagraphs ne null,
            )
        )
    }

    suspend fun countYoudao(providerId: String, novelId: String): Long {
        return col.countDocuments(
            and(
                NovelChapter::providerId eq providerId,
                NovelChapter::novelId eq novelId,
                NovelChapter::youdaoParagraphs ne null,
            )
        )
    }

    // Element operations
    suspend fun getLocal(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): NovelChapter? {
        return col.findOne(
            byId(providerId, novelId, chapterId)
        )
    }

    private suspend fun getRemote(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<NovelChapter> {
        return provider
            .getChapter(providerId, novelId, chapterId)
            .map {
                NovelChapter(
                    providerId = providerId,
                    novelId = novelId,
                    chapterId = chapterId,
                    paragraphs = it.paragraphs,
                    baiduParagraphs = null,
                )
            }
    }

    suspend fun get(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<NovelChapter> {
        val episodeLocal = getLocal(providerId, novelId, chapterId)
        if (episodeLocal != null) return Result.success(episodeLocal)

        return getRemote(providerId, novelId, chapterId)
            .onSuccess {
                metadataRepository.updateChangeAt(providerId, novelId)
                col.insertOne(it)
            }
    }

    suspend fun deleteOne(
        providerId: String,
        novelId: String,
        chapterId: String,
    ) {
        col.deleteOne(byId(providerId, novelId, chapterId))
    }

    suspend fun updateBaidu(
        providerId: String,
        novelId: String,
        chapterId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: List<String>,
    ) {
        col.updateOne(
            byId(providerId, novelId, chapterId),
            combine(
                setValue(NovelChapter::baiduGlossaryUuid, glossaryUuid),
                setValue(NovelChapter::baiduGlossary, glossary),
                setValue(NovelChapter::baiduParagraphs, paragraphsZh)
            )
        )
    }

    suspend fun updateBaidu(
        providerId: String,
        novelId: String,
        chapterId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: Map<Int, String>,
    ) {
        col.updateOne(
            byId(providerId, novelId, chapterId),
            combine(
                listOf(
                    setValue(NovelChapter::baiduGlossaryUuid, glossaryUuid),
                    setValue(NovelChapter::baiduGlossary, glossary),
                ) + paragraphsZh.map { (index, textZh) ->
                    // hacky, fix https://github.com/Litote/kmongo/issues/415
                    Updates.set("paragraphsZh.${index}", textZh)
                }
            ),
        )
    }

    suspend fun updateYoudao(
        providerId: String,
        novelId: String,
        chapterId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: List<String>,
    ) {
        col.updateOne(
            byId(providerId, novelId, chapterId),
            combine(
                setValue(NovelChapter::youdaoGlossaryUuid, glossaryUuid),
                setValue(NovelChapter::youdaoGlossary, glossary),
                setValue(NovelChapter::youdaoParagraphs, paragraphsZh)
            )
        )
    }

    suspend fun updateYoudao(
        providerId: String,
        novelId: String,
        chapterId: String,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: Map<Int, String>,
    ) {
        col.updateOne(
            byId(providerId, novelId, chapterId),
            combine(
                listOf(
                    setValue(NovelChapter::youdaoGlossaryUuid, glossaryUuid),
                    setValue(NovelChapter::youdaoGlossary, glossary),
                ) + paragraphsZh.map { (index, textZh) ->
                    setValue(NovelChapter::youdaoParagraphs.pos(index), textZh)
                }
            ),
        )
    }
}