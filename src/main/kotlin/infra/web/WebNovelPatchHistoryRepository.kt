package infra.web

import com.mongodb.client.model.UpdateOptions
import infra.MongoDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.util.KMongoUtil.toBson
import java.time.LocalDateTime
import java.util.UUID

class WebNovelPatchHistoryRepository(
    private val mongo: MongoDataSource,
) {
    private val col
        get() = mongo.database.getCollection<NovelPatchHistory>("web-patch")

    init {
        runBlocking {
            col.ensureUniqueIndex(
                NovelPatchHistory::providerId,
                NovelPatchHistory::novelId,
            )
        }
    }

    companion object {
        private fun byId(providerId: String, novelId: String): Bson {
            return and(
                NovelPatchHistory::providerId eq providerId,
                NovelPatchHistory::novelId eq novelId,
            )
        }
    }

    // List operations
    @Serializable
    data class NovelPatchHistoryOutline(
        val providerId: String,
        @SerialName("bookId") val novelId: String,
        val titleJp: String,
        val titleZh: String?,
    )

    suspend fun list(
        page: Int,
        pageSize: Int,
    ): List<NovelPatchHistoryOutline> {
        return col
            .withDocumentClass<NovelPatchHistoryOutline>()
            .find()
            .sort(toBson("{ _id: -1 }"))
            .skip(page * pageSize)
            .limit(pageSize)
            .projection(
                NovelPatchHistoryOutline::providerId,
                NovelPatchHistoryOutline::novelId,
                NovelPatchHistoryOutline::titleJp,
                NovelPatchHistoryOutline::titleZh,
            )
            .toList()
    }

    suspend fun count(): Long {
        return col.countDocuments()
    }

    // Element operations
    @Serializable
    data class NovelPatchHistory(
        val providerId: String,
        @SerialName("bookId") val novelId: String,
        val titleJp: String,
        val titleZh: String?,
        val patches: List<Patch>,
    ) {
        @Serializable
        data class TextChange(
            val jp: String,
            val zhOld: String?,
            val zhNew: String,
        )

        @Serializable
        data class Patch(
            val uuid: String,
            val titleChange: TextChange?,
            val introductionChange: TextChange?,
            val glossary: Map<String, String>?,
            val tocChange: List<TextChange>,
            @Contextual val createAt: LocalDateTime,
        )
    }

    suspend fun findOne(providerId: String, novelId: String): NovelPatchHistory? =
        col.findOne(byId(providerId, novelId))

    suspend fun deleteOne(providerId: String, novelId: String) =
        col.deleteOne(byId(providerId, novelId))

    private suspend fun createIfNotExist(
        providerId: String,
        novelId: String,
        titleJp: String,
        titleZh: String?,
    ) {
        col.updateOne(
            byId(providerId, novelId),
            setValueOnInsert(
                NovelPatchHistory(
                    providerId = providerId,
                    novelId = novelId,
                    titleJp = titleJp,
                    titleZh = titleZh,
                    patches = emptyList(),
                )
            ),
            UpdateOptions().upsert(true),
        )
    }

    suspend fun addPatch(
        providerId: String,
        novelId: String,
        titleJp: String,
        titleZh: String?,
        titleChange: NovelPatchHistory.TextChange?,
        introductionChange: NovelPatchHistory.TextChange?,
        glossaryChange: Map<String, String>?,
        tocChange: List<NovelPatchHistory.TextChange>,
    ) {
        createIfNotExist(
            providerId = providerId,
            novelId = novelId,
            titleJp = titleJp,
            titleZh = titleZh,
        )
        val patch = NovelPatchHistory.Patch(
            uuid = UUID.randomUUID().toString(),
            titleChange = titleChange,
            introductionChange = introductionChange,
            tocChange = tocChange,
            glossary = glossaryChange,
            createAt = LocalDateTime.now(),
        )
        col.updateOne(
            byId(providerId, novelId),
            push(NovelPatchHistory::patches, patch),
        )
    }
}