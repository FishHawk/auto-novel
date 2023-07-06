package infra.web

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import infra.ElasticSearchDataSource
import infra.MongoDataSource
import infra.WebNovelFavoriteModel
import infra.WebNovelMetadataEsModel
import infra.model.*
import infra.provider.RemoteNovelListItem
import infra.provider.WebNovelProviderDataSource
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.id.toId
import util.Optional
import java.time.LocalDateTime
import java.util.*

object WebNovelFilter {
    enum class Type { 全部, 连载中, 已完结, 短篇 }
    enum class Level { 全部, 一般向, R18 }
}

private fun Enum<*>.serialName(): String =
    javaClass.getDeclaredField(name).getAnnotation(SerialName::class.java)!!.value

class WebNovelMetadataRepository(
    private val provider: WebNovelProviderDataSource,
    private val mongo: MongoDataSource,
    private val es: ElasticSearchDataSource,
) {
    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<List<WebNovelMetadataOutline>> {
        WebNovelType
        val ranks = provider.listRank(providerId, options)
            .getOrElse { return Result.failure(it) }
        val items = ranks.map {
            val novel = mongo.webNovelMetadataCollection
                .findOne(WebNovelMetadata.byId(providerId, it.novelId))
            it.toOutline(providerId, novel)
        }
        return Result.success(items)
    }

    suspend fun search(
        queryString: String?,
        filterProvider: String?,
        filterType: WebNovelFilter.Type,
        filterLevel: WebNovelFilter.Level,
        page: Int,
        pageSize: Int,
    ): Page<WebNovelMetadataOutline> {
        val response = es.client.search(
            ElasticSearchDataSource.webNovelIndexName,
            from = page * pageSize,
            size = pageSize
        ) {
            query = bool {
                filter(buildList {
                    if (filterProvider != null) {
                        add(term(WebNovelMetadataEsModel::providerId, filterProvider))
                    }
                    when (filterType) {
                        WebNovelFilter.Type.连载中 -> WebNovelType.连载中
                        WebNovelFilter.Type.已完结 -> WebNovelType.已完结
                        WebNovelFilter.Type.短篇 -> WebNovelType.短篇
                        else -> null
                    }?.let {
                        add(term(WebNovelMetadataEsModel::type, it.serialName()))
                    }
                    if (filterLevel == WebNovelFilter.Level.R18) add(
                        terms(
                            WebNovelMetadataEsModel::attentions,
                            WebNovelAttention.R18.serialName(),
                            WebNovelAttention.性描写.serialName(),
                        )
                    )
                })

                if (filterLevel == WebNovelFilter.Level.一般向) mustNot(
                    terms(
                        WebNovelMetadataEsModel::attentions,
                        WebNovelAttention.R18.serialName(),
                        WebNovelAttention.性描写.serialName(),
                    )
                )

                if (queryString != null) {
                    must(
                        disMax {
                            queries(
                                match(WebNovelMetadataEsModel::titleJp, queryString),
                                match(WebNovelMetadataEsModel::titleZh, queryString),
                                match(WebNovelMetadataEsModel::authors, queryString),
                            )
                        }
                    )
                } else {
                    sort {
                        add(WebNovelMetadataEsModel::changeAt)
                    }
                }
            }
        }
        val items = response.hits?.hits
            ?.map { hit ->
                val esNovel = hit.parseHit<WebNovelMetadataEsModel>()
                mongo.webNovelMetadataCollection
                    .findOne(WebNovelMetadata.byId(esNovel.providerId, esNovel.novelId))!!
                    .toOutline()
            }
            ?: emptyList()
        val total = response.total
        return Page(items = items, total = total)
    }

    suspend fun get(
        providerId: String,
        novelId: String,
    ): WebNovelMetadata? {
        return mongo
            .webNovelMetadataCollection
            .findOne(WebNovelMetadata.byId(providerId, novelId))
    }

    suspend fun getRemote(
        providerId: String,
        novelId: String,
    ): Result<WebNovelMetadata> {
        val remote = provider
            .getMetadata(providerId, novelId)
            .getOrElse { return Result.failure(it) }
        val model = WebNovelMetadata(
            id = ObjectId(),
            providerId = providerId,
            novelId = novelId,
            titleJp = remote.title,
            authors = remote.authors.map { WebNovelAuthor(it.name, it.link) },
            type = remote.type,
            keywords = remote.keywords,
            attentions = remote.attentions,
            introductionJp = remote.introduction,
            toc = remote.toc.map { WebNovelTocItem(it.title, null, it.chapterId, it.createAt) },
        )
        return Result.success(model)
    }

    suspend fun getRemoteAndSave(
        providerId: String,
        novelId: String,
    ): Result<WebNovelMetadata> {
        val remote = provider
            .getMetadata(providerId, novelId)
            .getOrElse { return Result.failure(it) }
        val model = WebNovelMetadata(
            id = ObjectId(),
            providerId = providerId,
            novelId = novelId,
            titleJp = remote.title,
            authors = remote.authors.map { WebNovelAuthor(it.name, it.link) },
            type = remote.type,
            keywords = remote.keywords,
            attentions = remote.attentions,
            introductionJp = remote.introduction,
            toc = remote.toc.map { WebNovelTocItem(it.title, null, it.chapterId) },
        )
        mongo
            .webNovelMetadataCollection
            .insertOne(model)
        syncEs(model, true)
        return Result.success(get(providerId, novelId)!!)
    }

    suspend fun exist(providerId: String, novelId: String): Boolean {
        return mongo
            .webNovelMetadataCollection
            .countDocuments(
                WebNovelMetadata.byId(providerId, novelId),
                CountOptions().limit(1),
            ) != 0L
    }

    suspend fun increaseVisited(providerId: String, novelId: String) {
        mongo
            .webNovelMetadataCollection
            .updateOne(
                WebNovelMetadata.byId(providerId, novelId),
                inc(WebNovelMetadata::visited, 1)
            )
    }

    suspend fun update(
        providerId: String,
        novelId: String,
        titleJp: String?,
        type: WebNovelType,
        attentions: List<WebNovelAttention>,
        keywords: List<String>,
        introductionJp: String?,
        toc: List<WebNovelTocItem>?,
        hasChanged: Boolean,
    ): WebNovelMetadata? {
        val list = mutableListOf(
            setValue(WebNovelMetadata::titleJp, titleJp),
            setValue(WebNovelMetadata::type, type),
            setValue(WebNovelMetadata::attentions, attentions),
            setValue(WebNovelMetadata::keywords, keywords),
            setValue(WebNovelMetadata::introductionJp, introductionJp),
            setValue(WebNovelMetadata::syncAt, LocalDateTime.now())
        )
        if (toc != null) {
            list.add(setValue(WebNovelMetadata::toc, toc))
        }
        if (hasChanged) {
            list.add(setValue(WebNovelMetadata::changeAt, LocalDateTime.now()))
        }

        val novel = mongo
            .webNovelMetadataCollection
            .findOneAndUpdate(
                WebNovelMetadata.byId(providerId, novelId),
                combine(list),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        if (novel != null) {
            syncEs(novel, hasChanged)
        }
        return novel
    }

    suspend fun updateWenkuId(
        providerId: String,
        novelId: String,
        wenkuId: String?,
    ): WebNovelMetadata? {
        return mongo
            .webNovelMetadataCollection
            .findOneAndUpdate(
                WebNovelMetadata.byId(providerId, novelId),
                setValue(WebNovelMetadata::wenkuId, wenkuId),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
    }

    suspend fun updateTranslateStateJp(
        providerId: String,
        novelId: String,
    ) {
        val jp = mongo.webNovelChapterCollection
            .countDocuments(
                WebNovelChapter.byNovelId(providerId, novelId)
            )
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

    suspend fun updateTranslateStateZh(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
    ): Long {
        val zhProperty1 = when (translatorId) {
            TranslatorId.Baidu -> WebNovelChapter::baiduParagraphs
            TranslatorId.Youdao -> WebNovelChapter::youdaoParagraphs
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

    suspend fun updateTranslateGptStateZh(
        providerId: String,
        novelId: String,
    ) {
        val zh = mongo.webNovelChapterCollection
            .countDocuments(
                and(
                    WebNovelChapter::providerId eq providerId,
                    WebNovelChapter::novelId eq novelId,
                    WebNovelChapter::gptParagraphs ne null,
                )
            )
        mongo
            .webNovelMetadataCollection
            .updateOne(
                WebNovelMetadata.byId(providerId, novelId),
                setValue(WebNovelMetadata::gpt, zh),
            )
    }

    suspend fun updateZh(
        providerId: String,
        novelId: String,
        titleZh: Optional<String?>,
        introductionZh: Optional<String?>,
        glossary: Optional<Map<String, String>>,
        tocZh: Map<Int, String?>,
    ): WebNovelMetadata? {
        val list = mutableListOf<Bson>()
        titleZh.ifSome {
            list.add(setValue(WebNovelMetadata::titleZh, it))
        }
        introductionZh.ifSome {
            list.add(setValue(WebNovelMetadata::introductionZh, it))
        }
        glossary.ifSome {
            list.add(setValue(WebNovelMetadata::glossaryUuid, UUID.randomUUID().toString()))
            list.add(setValue(WebNovelMetadata::glossary, it))
        }
        tocZh.forEach { (index, itemTitleZh) ->
            list.add(setValue(WebNovelMetadata::toc.pos(index) / WebNovelTocItem::titleZh, itemTitleZh))
        }
        list.add(setValue(WebNovelMetadata::changeAt, LocalDateTime.now()))

        return mongo
            .webNovelMetadataCollection
            .findOneAndUpdate(
                WebNovelMetadata.byId(providerId, novelId),
                combine(list),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
            ?.also { syncEs(it, false) }
    }

    private suspend fun syncEs(
        novel: WebNovelMetadata,
        hasChange: Boolean,
    ) {
        if (hasChange) {
            val updateAt = Clock.System.now()
            es.client.indexDocument(
                id = "${novel.providerId}.${novel.novelId}",
                target = ElasticSearchDataSource.webNovelIndexName,
                document = WebNovelMetadataEsModel(
                    providerId = novel.providerId,
                    novelId = novel.novelId,
                    titleJp = novel.titleJp,
                    titleZh = novel.titleZh,
                    authors = novel.authors.map { it.name },
                    type = novel.type,
                    attentions = novel.attentions,
                    changeAt = updateAt.epochSeconds,
                ),
                refresh = Refresh.WaitFor,
            )
            mongo.webNovelFavoriteCollection.updateMany(
                WebNovelFavoriteModel::novelId eq novel.id.toId(),
                setValue(WebNovelFavoriteModel::updateAt, updateAt)
            )
        } else {
            @Serializable
            data class EsNovelUpdate(
                val titleJp: String,
                val titleZh: String?,
                val authors: List<String>,
                val type: WebNovelType,
                val attentions: List<WebNovelAttention>,
            )

            val update = EsNovelUpdate(
                titleJp = novel.titleJp,
                titleZh = novel.titleZh,
                authors = novel.authors.map { it.name },
                type = novel.type,
                attentions = novel.attentions,
            )
            es.client.updateDocument(
                id = "${novel.providerId}.${novel.novelId}",
                target = ElasticSearchDataSource.webNovelIndexName,
                docJson = Json.encodeToString(update),
                refresh = Refresh.WaitFor,
            )
        }
    }
}

private fun RemoteNovelListItem.toOutline(
    providerId: String,
    novel: WebNovelMetadata?,
) =
    WebNovelMetadataOutline(
        providerId = providerId,
        novelId = novelId,
        titleJp = title,
        titleZh = novel?.titleZh,
        type = null,
        attentions = attentions,
        keywords = keywords,
        total = novel?.toc?.count { it.chapterId != null }?.toLong() ?: 0,
        jp = novel?.jp ?: 0,
        baidu = novel?.baidu ?: 0,
        youdao = novel?.youdao ?: 0,
        extra = extra,
    )

fun WebNovelMetadata.toOutline() =
    WebNovelMetadataOutline(
        providerId = providerId,
        novelId = novelId,
        titleJp = titleJp,
        titleZh = titleZh,
        type = type,
        attentions = attentions,
        keywords = keywords,
        total = toc.count { it.chapterId != null }.toLong(),
        jp = jp,
        baidu = baidu,
        youdao = youdao,
        extra = null,
    )