package infra.web

import com.mongodb.client.model.Updates
import infra.MongoDataSource
import infra.model.*
import infra.provider.RemoteChapter
import infra.provider.WebNovelProviderDataSource
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.aggregate
import java.time.LocalDateTime

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

    suspend fun getRemote(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<RemoteChapter> {
        return provider
            .getChapter(providerId, novelId, chapterId)
    }

    suspend fun getOrSyncRemote(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<WebNovelChapter> {
        val local = get(providerId, novelId, chapterId)
        if (local != null) {
            return Result.success(local)
        }

        return getRemote(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
        ).map {
            WebNovelChapter(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                paragraphs = it.paragraphs,
            )
        }.onSuccess {
            mongo
                .webNovelChapterCollection
                .insertOne(it)
            updateMetadataJp(providerId, novelId)
        }
    }

    private suspend fun updateMetadataJp(
        providerId: String,
        novelId: String,
    ) {
        val jp = mongo.webNovelChapterCollection
            .countDocuments(WebNovelChapter.byNovelId(providerId, novelId))
        mongo
            .webNovelMetadataCollection
            .updateOne(
                WebNovelMetadata.byId(providerId, novelId),
                combine(
                    setValue(WebNovelMetadata::jp, jp),
                    setValue(WebNovelMetadata::changeAt, LocalDateTime.now()),
                ),
            )
    }

    suspend fun replace(
        providerId: String,
        novelId: String,
        chapterId: String,
        paragraphs: List<String>,
    ) {
        mongo
            .webNovelChapterCollection
            .replaceOne(
                WebNovelChapter.byId(providerId, novelId, chapterId),
                WebNovelChapter(
                    providerId = providerId,
                    novelId = novelId,
                    chapterId = chapterId,
                    paragraphs = paragraphs,
                ),
            )
    }

    // Translation
    suspend fun updateTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossary: Glossary?,
        paragraphsZh: List<String>,
    ) {
        val updateBson = when (translatorId) {
            TranslatorId.Baidu -> combine(
                setValue(WebNovelChapter::baiduGlossaryUuid, glossary?.id),
                setValue(WebNovelChapter::baiduGlossary, glossary?.map),
                setValue(WebNovelChapter::baiduParagraphs, paragraphsZh)
            )

            TranslatorId.Youdao -> combine(
                setValue(WebNovelChapter::youdaoGlossaryUuid, glossary?.id),
                setValue(WebNovelChapter::youdaoGlossary, glossary?.map),
                setValue(WebNovelChapter::youdaoParagraphs, paragraphsZh)
            )

            TranslatorId.Gpt -> combine(
                // GPT暂不支持术语表
                setValue(WebNovelChapter::gptParagraphs, paragraphsZh)
            )
        }
        mongo
            .webNovelChapterCollection
            .updateOne(
                WebNovelChapter.byId(providerId, novelId, chapterId),
                updateBson,
            )
    }

    suspend fun updateTranslationPartially(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossary: Glossary?,
        paragraphsZh: Map<Int, String>,
    ) {
        val updateBson = when (translatorId) {
            TranslatorId.Baidu -> combine(
                listOf(
                    setValue(WebNovelChapter::baiduGlossaryUuid, glossary?.id),
                    setValue(WebNovelChapter::baiduGlossary, glossary?.map),
                ) + paragraphsZh.map { (index, textZh) ->
                    // hacky, fix https://github.com/Litote/kmongo/issues/415
                    Updates.set("paragraphsZh.${index}", textZh)
                }
            )

            TranslatorId.Youdao -> combine(
                listOf(
                    setValue(WebNovelChapter::youdaoGlossaryUuid, glossary?.id),
                    setValue(WebNovelChapter::youdaoGlossary, glossary?.map),
                ) + paragraphsZh.map { (index, textZh) ->
                    setValue(WebNovelChapter::youdaoParagraphs.pos(index), textZh)
                }
            )

            TranslatorId.Gpt -> combine(
                // GPT暂不支持术语表
            )
        }
        mongo
            .webNovelChapterCollection
            .updateOne(
                WebNovelChapter.byId(providerId, novelId, chapterId),
                updateBson,
            )
    }
}