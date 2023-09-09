package infra.web

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
    suspend fun getTranslationOutlines(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
    ): List<WebNovelChapterTranslationState> {
        val (glossaryUuidProperty, paragraphsZhProperty) = when (translatorId) {
            TranslatorId.Baidu -> Pair(WebNovelChapter::baiduGlossaryUuid, WebNovelChapter::baiduParagraphs)
            TranslatorId.Youdao -> Pair(WebNovelChapter::youdaoGlossaryUuid, WebNovelChapter::youdaoParagraphs)
            TranslatorId.Gpt -> Pair(WebNovelChapter::gptGlossaryUuid, WebNovelChapter::gptParagraphs)
        }
        return mongo
            .webNovelChapterCollection
            .aggregate<WebNovelChapterTranslationState>(
                match(WebNovelChapter.byNovelId(providerId, novelId)),
                project(
                    WebNovelChapterTranslationState::chapterId from WebNovelChapter::chapterId,
                    WebNovelChapterTranslationState::glossaryUuid from glossaryUuidProperty,
                    WebNovelChapterTranslationState::translated from cond(paragraphsZhProperty, true, false),
                ),
            )
            .toList()
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

    private suspend fun getRemote(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<WebNovelChapter> {
        return provider
            .getChapter(providerId, novelId, chapterId)
            .map {
                WebNovelChapter(
                    providerId = providerId,
                    novelId = novelId,
                    chapterId = chapterId,
                    paragraphs = it.paragraphs,
                )
            }
    }

    suspend fun getOrSyncRemote(
        providerId: String,
        novelId: String,
        chapterId: String,
        forceSync: Boolean = false,
    ): Result<WebNovelChapter> {
        val local = get(providerId, novelId, chapterId)
        if (!forceSync && local != null) {
            return Result.success(local)
        }

        val remote = getRemote(providerId, novelId, chapterId)
            .getOrElse { return Result.failure(it) }

        if (local == null) {
            // 本地不存在
            mongo
                .webNovelChapterCollection
                .insertOne(remote)
            updateMetadataJp(providerId, novelId)
            return Result.success(remote)
        } else if (remote.paragraphs != local.paragraphs) {
            // 本地存在，但不是最新
            mongo
                .webNovelChapterCollection
                .replaceOne(
                    WebNovelChapter.byId(providerId, novelId, chapterId),
                    remote,
                )
            if (local.baiduParagraphs != null) {
                updateChapterTranslateState(providerId, novelId, TranslatorId.Baidu)
            }
            if (local.youdaoParagraphs != null) {
                updateChapterTranslateState(providerId, novelId, TranslatorId.Youdao)
            }
            if (local.gptParagraphs != null) {
                updateChapterTranslateState(providerId, novelId, TranslatorId.Gpt)
            }
            return Result.success(remote)
        } else {
            // 本地存在，且已是最新
            return Result.success(local)
        }
    }

    suspend fun updateTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossary: Glossary?,
        paragraphsZh: List<String>,
    ): Long {
        val updateBson = when (translatorId) {
            TranslatorId.Baidu -> combine(
                setValue(WebNovelChapter::baiduGlossaryUuid, glossary?.id),
                setValue(WebNovelChapter::baiduGlossary, glossary?.map ?: emptyMap()),
                setValue(WebNovelChapter::baiduParagraphs, paragraphsZh)
            )

            TranslatorId.Youdao -> combine(
                setValue(WebNovelChapter::youdaoGlossaryUuid, glossary?.id),
                setValue(WebNovelChapter::youdaoGlossary, glossary?.map ?: emptyMap()),
                setValue(WebNovelChapter::youdaoParagraphs, paragraphsZh)
            )

            TranslatorId.Gpt -> combine(
                setValue(WebNovelChapter::gptGlossaryUuid, glossary?.id),
                setValue(WebNovelChapter::gptGlossary, glossary?.map ?: emptyMap()),
                setValue(WebNovelChapter::gptParagraphs, paragraphsZh)
            )
        }
        mongo
            .webNovelChapterCollection
            .updateOne(
                WebNovelChapter.byId(providerId, novelId, chapterId),
                updateBson,
            )

        return updateChapterTranslateState(
            providerId = providerId,
            novelId = novelId,
            translatorId = translatorId,
        )
    }

    private suspend fun updateChapterTranslateState(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
    ): Long {
        val zhProperty1 = when (translatorId) {
            TranslatorId.Baidu -> WebNovelChapter::baiduParagraphs
            TranslatorId.Youdao -> WebNovelChapter::youdaoParagraphs
            TranslatorId.Gpt -> WebNovelChapter::gptParagraphs
        }
        val zh = mongo.webNovelChapterCollection
            .countDocuments(
                and(
                    WebNovelChapter::providerId eq providerId,
                    WebNovelChapter::novelId eq novelId,
                    zhProperty1 ne null,
                )
            )
        val zhProperty = when (translatorId) {
            TranslatorId.Baidu -> WebNovelMetadata::baidu
            TranslatorId.Youdao -> WebNovelMetadata::youdao
            TranslatorId.Gpt -> WebNovelMetadata::gpt
        }
        mongo
            .webNovelMetadataCollection
            .updateOne(
                WebNovelMetadata.byId(providerId, novelId),
                combine(
                    setValue(zhProperty, zh),
                    setValue(WebNovelMetadata::changeAt, LocalDateTime.now()),
                ),
            )
        return zh
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
}