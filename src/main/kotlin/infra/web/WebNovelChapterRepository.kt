package infra.web

import com.mongodb.client.model.Updates
import infra.MongoDataSource
import infra.model.TranslatorId
import infra.model.WebNovelChapter
import infra.model.WebNovelChapterOutline
import infra.provider.RemoteChapter
import infra.provider.WebNovelProviderDataSource
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import java.lang.RuntimeException

class WebNovelChapterRepository(
    private val provider: WebNovelProviderDataSource,
    private val mongo: MongoDataSource,
) {
    suspend fun getOutline(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): WebNovelChapterOutline? {
        return mongo
            .webNovelChapterCollection
            .aggregate<WebNovelChapterOutline>(
                match(WebNovelChapter.byId(providerId, novelId, chapterId)),
                limit(1),
                project(
                    WebNovelChapterOutline::baiduGlossaryUuid from WebNovelChapter::baiduGlossaryUuid,
                    WebNovelChapterOutline::youdaoGlossaryUuid from WebNovelChapter::youdaoGlossaryUuid,
                    WebNovelChapterOutline::baiduExist from cond(WebNovelChapter::baiduParagraphs, true, false),
                    WebNovelChapterOutline::youdaoExist from cond(WebNovelChapter::youdaoParagraphs, true, false),
                    WebNovelChapterOutline::gptExist from cond(WebNovelChapter::gptParagraphs, true, false),
                ),
            )
            .first()
    }

    suspend fun get(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): WebNovelChapter? {
        return mongo
            .webNovelChapterCollection
            .findOne(WebNovelChapter.byId(providerId, novelId, chapterId))
    }

    suspend fun create(
        providerId: String,
        novelId: String,
        chapterId: String,
        paragraphs: List<String>,
    ): WebNovelChapter {
        val model = WebNovelChapter(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            paragraphs = paragraphs,
        )
        mongo
            .webNovelChapterCollection
            .insertOne(model)
        return model
    }

    suspend fun replace(
        providerId: String,
        novelId: String,
        chapterId: String,
        paragraphs: List<String>,
    ): WebNovelChapter {
        val model = WebNovelChapter(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            paragraphs = paragraphs,
        )
        mongo
            .webNovelChapterCollection
            .replaceOne(WebNovelChapter.byId(providerId, novelId, chapterId), model)
        return model
    }

    suspend fun delete(
        providerId: String,
        novelId: String,
        chapterId: String,
    ) {
        mongo
            .webNovelChapterCollection
            .deleteOne(WebNovelChapter.byId(providerId, novelId, chapterId))
    }

    // Update
    suspend fun getRemote(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<RemoteChapter> {
        return provider.getChapter(providerId, novelId, chapterId)
    }

    suspend fun getRemoteAndSave(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<WebNovelChapter> {
        val remote = provider
            .getChapter(providerId, novelId, chapterId)
            .getOrElse { return Result.failure(it) }
        val model = create(
            providerId,
            novelId,
            chapterId,
            remote.paragraphs,
        )
        return Result.success(model)
    }

    // Translation
    suspend fun updateTranslationWithGlossary(
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

            else -> throw RuntimeException("翻译器不支持术语表")
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
        paragraphsZh: List<String>,
    ) {
        val updateBson = when (translatorId) {
            TranslatorId.Gpt ->
                setValue(WebNovelChapter::gptParagraphs, paragraphsZh)

            else -> throw RuntimeException("翻译器需要术语表")
        }
        mongo
            .webNovelChapterCollection
            .updateOne(WebNovelChapter.byId(providerId, novelId, chapterId), updateBson)
    }

    suspend fun updateTranslationWithGlossary(
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

            else -> throw RuntimeException("翻译器不支持术语表")
        }
        mongo
            .webNovelChapterCollection
            .updateOne(WebNovelChapter.byId(providerId, novelId, chapterId), updateBson)
    }
}