package infra.web.repository

import com.mongodb.client.model.Aggregates.match
import com.mongodb.client.model.Aggregates.project
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.*
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import infra.*
import infra.common.Glossary
import infra.common.TranslatorId
import infra.web.WebNovelChapter
import infra.web.WebNovelChapterTranslationState
import infra.web.WebNovelMetadata
import infra.web.datasource.WebNovelHttpDataSource
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock

class WebNovelChapterRepository(
    private val provider: WebNovelHttpDataSource,
    mongo: MongoClient,
) {
    private val webNovelMetadataCollection =
        mongo.database.getCollection<WebNovelMetadata>(
            MongoCollectionNames.WEB_NOVEL,
        )
    private val webNovelChapterCollection =
        mongo.database.getCollection<WebNovelChapter>(
            MongoCollectionNames.WEB_CHAPTER,
        )

    suspend fun getTranslationOutlines(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
    ): List<WebNovelChapterTranslationState> {
        val (glossaryUuidProperty, paragraphsZhProperty) = when (translatorId) {
            TranslatorId.Baidu -> Pair(WebNovelChapter::baiduGlossaryUuid, WebNovelChapter::baiduParagraphs)
            TranslatorId.Youdao -> Pair(WebNovelChapter::youdaoGlossaryUuid, WebNovelChapter::youdaoParagraphs)
            TranslatorId.Gpt -> Pair(WebNovelChapter::gptGlossaryUuid, WebNovelChapter::gptParagraphs)
            TranslatorId.Sakura -> Pair(WebNovelChapter::sakuraGlossaryUuid, WebNovelChapter::sakuraParagraphs)
        }
        return webNovelChapterCollection
            .aggregate<WebNovelChapterTranslationState>(
                match(WebNovelChapter.byNovelId(providerId, novelId)),
                project(
                    fields(
                        include(
                            WebNovelChapterTranslationState::sakuraVersion.field(),
                            WebNovelChapterTranslationState::chapterId.field(),
                        ),
                        computed(
                            WebNovelChapterTranslationState::glossaryUuid.field(),
                            glossaryUuidProperty.field(),
                        ),
                        computed(
                            WebNovelChapterTranslationState::translated.field(),
                            cond(paragraphsZhProperty, true, false),
                        ),
                    )
                ),
            )
            .toList()
    }

    suspend fun get(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): WebNovelChapter? {
        return webNovelChapterCollection
            .find(WebNovelChapter.byId(providerId, novelId, chapterId))
            .firstOrNull()
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
            webNovelChapterCollection
                .insertOne(remote)
            updateMetadataJp(providerId, novelId)
            return Result.success(remote)
        } else if (remote.paragraphs != local.paragraphs) {
            // 本地存在，但不是最新
            webNovelChapterCollection
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
        val glossaryUuid = glossary?.id ?: "no glossary"
        val glossaryContent = glossary?.map ?: emptyMap()
        val updateBson = when (translatorId) {
            TranslatorId.Baidu -> combine(
                set(WebNovelChapter::baiduGlossaryUuid.field(), glossaryUuid),
                set(WebNovelChapter::baiduGlossary.field(), glossaryContent),
                set(WebNovelChapter::baiduParagraphs.field(), paragraphsZh)
            )

            TranslatorId.Youdao -> combine(
                set(WebNovelChapter::youdaoGlossaryUuid.field(), glossaryUuid),
                set(WebNovelChapter::youdaoGlossary.field(), glossaryContent),
                set(WebNovelChapter::youdaoParagraphs.field(), paragraphsZh)
            )

            TranslatorId.Gpt -> combine(
                set(WebNovelChapter::gptGlossaryUuid.field(), glossaryUuid),
                set(WebNovelChapter::gptGlossary.field(), glossaryContent),
                set(WebNovelChapter::gptParagraphs.field(), paragraphsZh)
            )

            TranslatorId.Sakura -> combine(
                set(WebNovelChapter::sakuraVersion.field(), "0.9"),
                set(WebNovelChapter::sakuraGlossaryUuid.field(), glossaryUuid),
                set(WebNovelChapter::sakuraGlossary.field(), glossaryContent),
                set(WebNovelChapter::sakuraParagraphs.field(), paragraphsZh)
            )
        }
        webNovelChapterCollection
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
            TranslatorId.Sakura -> WebNovelChapter::sakuraParagraphs
        }
        val zh = webNovelChapterCollection
            .countDocuments(
                and(
                    eq(WebNovelChapter::providerId.field(), providerId),
                    eq(WebNovelChapter::novelId.field(), novelId),
                    ne(zhProperty1.field(), null),
                )
            )
        val zhProperty = when (translatorId) {
            TranslatorId.Baidu -> WebNovelMetadata::baidu
            TranslatorId.Youdao -> WebNovelMetadata::youdao
            TranslatorId.Gpt -> WebNovelMetadata::gpt
            TranslatorId.Sakura -> WebNovelMetadata::sakura
        }
        webNovelMetadataCollection
            .updateOne(
                WebNovelMetadata.byId(providerId, novelId),
                combine(
                    set(zhProperty.field(), zh),
                    set(WebNovelMetadata::changeAt.field(), Clock.System.now()),
                ),
            )
        return zh
    }

    private suspend fun updateMetadataJp(
        providerId: String,
        novelId: String,
    ) {
        val jp = webNovelChapterCollection
            .countDocuments(WebNovelChapter.byNovelId(providerId, novelId))
        webNovelMetadataCollection
            .updateOne(
                WebNovelMetadata.byId(providerId, novelId),
                combine(
                    set(WebNovelMetadata::jp.field(), jp),
                    set(WebNovelMetadata::changeAt.field(), Clock.System.now()),
                ),
            )
    }
}