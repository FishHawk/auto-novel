package data

import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.litote.kmongo.*
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class BookPatch(
    val providerId: String,
    val bookId: String,
    val metadataPatches: List<BookMetadataPatch>,
    val episodePatches: Map<String, List<BookEpisodePatch>>,
)

@Serializable
data class BookMetadataPatch(
    val uuid: String,
    val titleChange: TextChange?,
    val introductionChange: TextChange?,
    val tocChange: Map<String, TextChange>,
    @Contextual val createAt: LocalDateTime,
) {
    @Serializable
    data class TextChange(
        val zhOld: String?,
        val zhNew: String,
    )
}

@Serializable
data class BookEpisodePatch(
    val uuid: String,
    val paragraphsChange: Map<Int, TextChange>,
    @Contextual val createAt: LocalDateTime,
) {
    @Serializable
    data class TextChange(
        val zhOld: String,
        val zhNew: String,
    )
}

class BookMetadataPatchRepository(
    private val mongoDataSource: MongoDataSource,
    private val bookMetadataRepository: BookMetadataRepository,
    private val bookEpisodeRepository: BookEpisodeRepository,
) {
    private val col
        get() = mongoDataSource.database.getCollection<BookPatch>("patch")

    init {
        runBlocking {
            col.ensureUniqueIndex(
                BookPatch::providerId,
                BookPatch::bookId,
            )
        }
    }

    private fun bsonSpecifyPatch(providerId: String, bookId: String): Bson {
        return and(
            BookPatch::providerId eq providerId,
            BookPatch::bookId eq bookId,
        )
    }

    suspend fun addMetadataPatch(
        providerId: String,
        bookId: String,
        title: String?,
        introduction: String?,
        toc: Map<String, String>,
    ) {
        val metadata = bookMetadataRepository.getLocal(providerId, bookId)
            ?: return

        fun createTextChangeOrNull(zhOld: String?, zhNew: String?): BookMetadataPatch.TextChange? {
            return if (zhNew != null && zhNew != zhOld) {
                BookMetadataPatch.TextChange(zhOld, zhNew)
            } else null
        }

        val titleChange = createTextChangeOrNull(metadata.titleZh, title)
        val introductionChange = createTextChangeOrNull(metadata.introductionZh, introduction)
        val tocChange = toc.mapNotNull { (jp, zhNew) ->
            metadata.toc.find { it.titleJp == jp }?.let { item ->
                jp to BookMetadataPatch.TextChange(item.titleZh, zhNew)
            }
        }.toMap()

        if (titleChange != null ||
            introduction != null ||
            tocChange.isNotEmpty()
        ) {
            // Save patch
            col.updateOne(
                bsonSpecifyPatch(providerId, bookId),
                and(
                    setValueOnInsert(
                        BookPatch(
                            providerId = providerId,
                            bookId = bookId,
                            metadataPatches = emptyList(),
                            episodePatches = emptyMap(),
                        )
                    ),
                    push(
                        BookPatch::metadataPatches,
                        BookMetadataPatch(
                            uuid = UUID.randomUUID().toString(),
                            titleChange = titleChange,
                            introductionChange = introductionChange,
                            tocChange = tocChange,
                            createAt = LocalDateTime.now(),
                        )
                    ),
                ),
                UpdateOptions().upsert(true),
            )

            // Apply patch
            bookMetadataRepository.updateZh(
                providerId = providerId,
                bookId = bookId,
                titleZh = title,
                introductionZh = introduction,
                tocZh = toc,
            )
        }
    }

    suspend fun addEpisodePatch(
        providerId: String,
        bookId: String,
        episodeId: String,
        paragraphs: Map<Int, String>,
    ) {
        val episode = bookEpisodeRepository.getLocal(providerId, bookId, episodeId)
        if (episode?.paragraphsZh == null) {
            return
        }

        val paragraphsChange = paragraphs.mapNotNull { (index, zhNew) ->
            episode.paragraphsZh
                .getOrNull(index)
                ?.takeIf { zhOld -> zhOld != zhNew }
                ?.let { zhOld -> index to BookEpisodePatch.TextChange(zhOld, zhNew) }
        }.toMap()

        val list = paragraphsChange.map { (index, change) ->
            setValue(BookEpisode::paragraphsZh.pos(index), change.zhNew)
        }

        col.updateOne(
            bsonSpecifyPatch(providerId, bookId),
            and(
                setValueOnInsert(
                    BookPatch(
                        providerId = providerId,
                        bookId = bookId,
                        metadataPatches = emptyList(),
                        episodePatches = emptyMap(),
                    )
                ),
                push(
                    BookPatch::episodePatches.keyProjection(episodeId),
                    BookEpisodePatch(
                        uuid = UUID.randomUUID().toString(),
                        paragraphsChange = paragraphsChange,
                        createAt = LocalDateTime.now(),
                    )
                ),
            ),
            UpdateOptions().upsert(true),
        )
    }

    suspend fun confirmPatchesBeforeThis(
        providerId: String,
        bookId: String,
        id: String,
    ) {
    }

    suspend fun resetPatch(
        id: String,
    ) {

    }
}