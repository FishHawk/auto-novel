package infra.web

import com.mongodb.client.model.UpdateOptions
import infra.MongoDataSource
import infra.model.Page
import infra.model.WebNovelPatchHistory
import infra.model.WebNovelPatchHistoryOutline
import kotlinx.datetime.Clock
import org.bson.types.ObjectId
import org.litote.kmongo.push
import org.litote.kmongo.setValueOnInsert
import org.litote.kmongo.util.KMongoUtil.toBson
import java.util.*

class WebNovelPatchHistoryRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun list(
        page: Int,
        pageSize: Int,
    ): Page<WebNovelPatchHistoryOutline> {
        val total = mongo
            .webNovelPatchHistoryCollection
            .countDocuments()
        val items = mongo
            .webNovelPatchHistoryCollection
            .withDocumentClass<WebNovelPatchHistoryOutline>()
            .find()
            .sort(toBson("{ _id: -1 }"))
            .skip(page * pageSize)
            .limit(pageSize)
            .projection(
                WebNovelPatchHistoryOutline::providerId,
                WebNovelPatchHistoryOutline::novelId,
                WebNovelPatchHistoryOutline::titleJp,
                WebNovelPatchHistoryOutline::titleZh,
            )
            .toList()
        return Page(items = items, total = total)
    }

    suspend fun get(providerId: String, novelId: String): WebNovelPatchHistory? {
        return mongo
            .webNovelPatchHistoryCollection
            .findOne(WebNovelPatchHistory.byId(providerId, novelId))
    }

    suspend fun delete(providerId: String, novelId: String) {
        mongo
            .webNovelPatchHistoryCollection
            .deleteOne(WebNovelPatchHistory.byId(providerId, novelId))
    }

    private suspend fun createIfNotExist(
        providerId: String,
        novelId: String,
        titleJp: String,
        titleZh: String?,
    ) {
        mongo
            .webNovelPatchHistoryCollection
            .updateOne(
                WebNovelPatchHistory.byId(providerId, novelId),
                setValueOnInsert(
                    WebNovelPatchHistory(
                        id = ObjectId(),
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
        titleChange: WebNovelPatchHistory.TextChange?,
        introductionChange: WebNovelPatchHistory.TextChange?,
        glossaryChange: Map<String, String>?,
        tocChange: List<WebNovelPatchHistory.TextChange>,
    ) {
        createIfNotExist(
            providerId = providerId,
            novelId = novelId,
            titleJp = titleJp,
            titleZh = titleZh,
        )
        val patch = WebNovelPatchHistory.Patch(
            uuid = UUID.randomUUID().toString(),
            titleChange = titleChange,
            introductionChange = introductionChange,
            tocChange = tocChange,
            glossary = glossaryChange,
            createAt = Clock.System.now(),
        )
        mongo
            .webNovelPatchHistoryCollection
            .updateOne(
                WebNovelPatchHistory.byId(providerId, novelId),
                push(WebNovelPatchHistory::patches, patch),
            )
    }
}