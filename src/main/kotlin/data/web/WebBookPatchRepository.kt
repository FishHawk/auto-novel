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
data class BookPatches(
    val providerId: String,
    val bookId: String,
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

@Serializable
data class BookPatchOutline(
    val providerId: String,
    val bookId: String,
    val titleJp: String,
    val titleZh: String?,
)


class WebBookPatchRepository(private val mongo: MongoDataSource) {
    private val col
        get() = mongo.database.getCollection<BookPatches>("web-patch")

    init {
        runBlocking {
            col.ensureUniqueIndex(
                BookPatches::providerId,
                BookPatches::bookId,
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
            BookPatches::providerId eq providerId,
            BookPatches::bookId eq bookId,
        )
    }

    suspend fun findOne(
        providerId: String,
        bookId: String,
    ): BookPatches? {
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
                BookPatches(
                    providerId = providerId,
                    bookId = bookId,
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
        bookId: String,
        titleJp: String,
        titleZh: String?,
        titleChange: BookPatches.TextChange?,
        introductionChange: BookPatches.TextChange?,
        glossaryChange: Map<String, String>?,
        tocChange: List<BookPatches.TextChange>,
    ) {
        createIfNotExist(
            providerId = providerId,
            bookId = bookId,
            titleJp = titleJp,
            titleZh = titleZh,
        )
        val patch = BookPatches.Patch(
            uuid = UUID.randomUUID().toString(),
            titleChange = titleChange,
            introductionChange = introductionChange,
            tocChange = tocChange,
            glossary = glossaryChange,
            createAt = LocalDateTime.now(),
        )
        col.updateOne(
            bsonSpecifyPatch(providerId, bookId),
            push(BookPatches::patches, patch),
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