package infra.web

import com.mongodb.client.model.Updates
import infra.model.TranslationState
import infra.model.TranslatorId
import infra.MongoDataSource
import infra.model.WebNovelChapter
import infra.model.WebNovelMetadata
import infra.provider.WebNovelProviderDataSource
import org.litote.kmongo.*

class WebNovelChapterRepository(
    private val provider: WebNovelProviderDataSource,
    private val mongo: MongoDataSource,
) {
    suspend fun get(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): WebNovelChapter? {
        return mongo
            .webNovelChapterCollection
            .findOne(WebNovelChapter.byId(providerId, novelId, chapterId))
    }

    suspend fun getRemoteAndSave(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<WebNovelChapter> {
        val remote = provider
            .getChapter(providerId, novelId, chapterId)
            .getOrElse { return Result.failure(it) }
        val model = WebNovelChapter(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            paragraphs = remote.paragraphs,
        )
        mongo
            .webNovelChapterCollection
            .insertOne(model)
        return Result.success(model)
    }

    private suspend fun countTotal(providerId: String, novelId: String): Long? {
        return mongo.webNovelMetadataCollection
            .findOne(WebNovelMetadata.byId(providerId, novelId))
            ?.toc
            ?.count { it.chapterId != null }
            ?.toLong()
            ?: return null
    }

    private suspend fun countJp(providerId: String, novelId: String): Long {
        return mongo.webNovelChapterCollection
            .countDocuments(
                WebNovelChapter.byNovelId(providerId, novelId)
            )
    }

    private suspend fun countBaidu(providerId: String, novelId: String): Long {
        return mongo.webNovelChapterCollection
            .countDocuments(
                and(
                    WebNovelChapter::providerId eq providerId,
                    WebNovelChapter::novelId eq novelId,
                    WebNovelChapter::baiduParagraphs ne null,
                )
            )
    }

    private suspend fun countYoudao(providerId: String, novelId: String): Long {
        return mongo.webNovelChapterCollection
            .countDocuments(
                and(
                    WebNovelChapter::providerId eq providerId,
                    WebNovelChapter::novelId eq novelId,
                    WebNovelChapter::youdaoParagraphs ne null,
                )
            )
    }

    suspend fun findState(
        providerId: String,
        novelId: String,
    ): TranslationState? {
        val total = countTotal(providerId, novelId) ?: return null
        return TranslationState(
            total = total,
            baidu = countBaidu(providerId, novelId),
            youdao = countYoudao(providerId, novelId),
        )
    }

    suspend fun updateTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: List<String>,
    ) {
        val updateBson = when (translatorId) {
            TranslatorId.Baidu -> combine(
                setValue(WebNovelChapter::baiduGlossaryUuid, glossaryUuid),
                setValue(WebNovelChapter::baiduGlossary, glossary),
                setValue(WebNovelChapter::baiduParagraphs, paragraphsZh)
            )

            TranslatorId.Youdao -> combine(
                setValue(WebNovelChapter::youdaoGlossaryUuid, glossaryUuid),
                setValue(WebNovelChapter::youdaoGlossary, glossary),
                setValue(WebNovelChapter::youdaoParagraphs, paragraphsZh)
            )
        }
        mongo
            .webNovelChapterCollection
            .updateOne(WebNovelChapter.byId(providerId, novelId, chapterId), updateBson)
    }

    suspend fun updateTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossaryUuid: String?,
        glossary: Map<String, String>,
        paragraphsZh: Map<Int, String>,
    ) {
        val updateBson = when (translatorId) {
            TranslatorId.Baidu -> combine(
                listOf(
                    setValue(WebNovelChapter::baiduGlossaryUuid, glossaryUuid),
                    setValue(WebNovelChapter::baiduGlossary, glossary),
                ) + paragraphsZh.map { (index, textZh) ->
                    // hacky, fix https://github.com/Litote/kmongo/issues/415
                    Updates.set("paragraphsZh.${index}", textZh)
                }
            )

            TranslatorId.Youdao -> combine(
                listOf(
                    setValue(WebNovelChapter::youdaoGlossaryUuid, glossaryUuid),
                    setValue(WebNovelChapter::youdaoGlossary, glossary),
                ) + paragraphsZh.map { (index, textZh) ->
                    setValue(WebNovelChapter::youdaoParagraphs.pos(index), textZh)
                }
            )
        }
        mongo
            .webNovelChapterCollection
            .updateOne(WebNovelChapter.byId(providerId, novelId, chapterId), updateBson)
    }
}