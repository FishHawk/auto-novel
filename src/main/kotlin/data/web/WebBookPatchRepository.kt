package data.web

import com.mongodb.client.model.UpdateOptions
import data.MongoDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import org.litote.kmongo.util.KMongoUtil.toBson
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class BookPatch(
    val providerId: String,
    val bookId: String,
    val titleJp: String,
    val titleZh: String?,
    val patches: List<BookMetadataPatch>,
    val toc: Map<String, BookEpisodePatches>,
)

@Serializable
data class BookPatchOutline(
    val providerId: String,
    val bookId: String,
    val titleJp: String,
    val titleZh: String?,
)

@Serializable
data class BookMetadataPatch(
    val uuid: String,
    val titleChange: TextChange?,
    val introductionChange: TextChange?,
    val glossary: Map<String, String>?,
    val tocChange: List<TextChange>,
    @Contextual val createAt: LocalDateTime,
) {
    @Serializable
    data class TextChange(
        val jp: String,
        val zhOld: String?,
        val zhNew: String,
    )
}

@Serializable
data class BookEpisodePatches(
    val titleJp: String,
    val titleZh: String?,
    val patches: List<BookEpisodePatch>,
)

@Serializable
data class BookEpisodePatch(
    val uuid: String,
    val paragraphsChange: List<TextChange>,
    @Contextual val createAt: LocalDateTime,
) {
    @Serializable
    data class TextChange(
        val index: Int,
        val jp: String,
        val zhOld: String,
        val zhNew: String,
    )
}

class WebBookPatchRepository(private val mongo: MongoDataSource) {
    private val col
        get() = mongo.database.getCollection<BookPatch>("patch")

    init {
        runBlocking {
            col.ensureUniqueIndex(
                BookPatch::providerId,
                BookPatch::bookId,
            )
        }
    }

    // List operations
    suspend fun list(
        page: Int,
        pageSize: Int,
    ): List<BookPatchOutline> {
        return col
            .withDocumentClass<BookPatchOutline>()
            .find()
            .sort(toBson("{ _id: -1 }"))
            .skip(page * pageSize)
            .limit(pageSize)
            .projection(
                BookPatchOutline::providerId,
                BookPatchOutline::bookId,
                BookPatchOutline::titleJp,
                BookPatchOutline::titleZh,
            )
            .toList()
    }

    suspend fun count(): Long {
        return col.countDocuments()
    }

    // Element operations
    private fun bsonSpecifyPatch(providerId: String, bookId: String): Bson {
        return and(
            BookPatch::providerId eq providerId,
            BookPatch::bookId eq bookId,
        )
    }

    suspend fun findOne(
        providerId: String,
        bookId: String,
    ): BookPatch? {
        return col.findOne(
            bsonSpecifyPatch(providerId, bookId),
        )
    }

    private suspend fun createIfNotExist(
        providerId: String,
        bookId: String,
        titleJp: String,
        titleZh: String?,
    ) {
        col.updateOne(
            bsonSpecifyPatch(providerId, bookId),
            setValueOnInsert(
                BookPatch(
                    providerId = providerId,
                    bookId = bookId,
                    titleJp = titleJp,
                    titleZh = titleZh,
                    patches = emptyList(),
                    toc = emptyMap(),
                )
            ),
            UpdateOptions().upsert(true),
        )
    }

    suspend fun addPatch(
        providerId: String,
        bookId: String,
        titleJp: String,
        titleZh: String?,
        titleChange: BookMetadataPatch.TextChange?,
        introductionChange: BookMetadataPatch.TextChange?,
        glossaryChange: Map<String, String>?,
        tocChange: List<BookMetadataPatch.TextChange>,
    ) {
        createIfNotExist(
            providerId = providerId,
            bookId = bookId,
            titleJp = titleJp,
            titleZh = titleZh,
        )
        val patch = BookMetadataPatch(
            uuid = UUID.randomUUID().toString(),
            titleChange = titleChange,
            introductionChange = introductionChange,
            tocChange = tocChange,
            glossary = glossaryChange,
            createAt = LocalDateTime.now(),
        )
        col.updateOne(
            bsonSpecifyPatch(providerId, bookId),
            push(BookPatch::patches, patch),
        )
    }

    suspend fun deletePatch(
        providerId: String,
        bookId: String,
    ) {
        col.deleteOne(
            bsonSpecifyPatch(providerId, bookId),
        )
    }
}