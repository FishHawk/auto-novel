package infra.web

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import infra.ElasticSearchDataSource
import infra.MongoDataSource
import infra.WebNovelMetadataEsModel
import infra.model.*
import infra.provider.RemoteNovelListItem
import infra.provider.WebNovelProviderDataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.conversions.Bson
import org.litote.kmongo.*
import util.Optional
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class WebNovelMetadataRepository(
    private val provider: WebNovelProviderDataSource,
    private val mongo: MongoDataSource,
    private val es: ElasticSearchDataSource,
) {
    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): Result<List<WebNovelMetadataOutline>> {
        val ranks = provider.listRank(providerId, options)
            .getOrElse { return Result.failure(it) }

        // TODO: 太慢了
        val items = ranks.map {
//            val titleZh = mongo.webNovelMetadataCollection
//                .projection(
//                    WebNovelMetadataModel::titleZh,
//                    WebNovelMetadataModel.byId(providerId, it.novelId)
//                )
//                .first()
            val titleZh = mongo.webNovelMetadataCollection
                .findOne(WebNovelMetadata.byId(providerId, it.novelId))
                ?.titleZh
            it.toOutline(providerId, titleZh)
        }
        return Result.success(items)
    }

    suspend fun search(
        queryString: String?,
        providerId: String?,
        page: Int,
        pageSize: Int,
    ): Page<WebNovelMetadataOutline> {
        val response = es.client.search(
            ElasticSearchDataSource.webNovelIndexName,
            from = page * pageSize,
            size = pageSize
        ) {
            query = bool {
                if (providerId != null) {
                    filter(
                        term(WebNovelMetadataEsModel::providerId, providerId)
                    )
                }
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
            ?.map { hit -> hit.parseHit<WebNovelMetadataEsModel>().toOutline() }
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
        shouldInsert: Boolean,
    ): Result<WebNovelMetadata> {
        val remote = provider
            .getMetadata(providerId, novelId)
            .getOrElse { return Result.failure(it) }
        val model = WebNovelMetadata(
            providerId = providerId,
            novelId = novelId,
            titleJp = remote.title,
            authors = remote.authors.map { WebNovelAuthor(it.name, it.link) },
            introductionJp = remote.introduction,
            toc = remote.toc.map { WebNovelTocItem(it.title, null, it.chapterId) },
        )
        if (shouldInsert) {
            mongo
                .webNovelMetadataCollection
                .insertOne(model)
            syncEs(model, true)
        }
        return Result.success(model)
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
        introductionJp: String?,
        toc: List<WebNovelTocItem>?,
        hasChanged: Boolean,
    ): WebNovelMetadata? {
        val list = mutableListOf<Bson>()
        titleJp?.let { list.add(setValue(WebNovelMetadata::titleJp, it)) }
        introductionJp?.let { list.add(setValue(WebNovelMetadata::introductionJp, it)) }
        toc?.let { list.add(setValue(WebNovelMetadata::toc, toc)) }
        list.add(setValue(WebNovelMetadata::syncAt, LocalDateTime.now()))
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

    suspend fun updateChangeAt(providerId: String, novelId: String) {
        mongo
            .webNovelMetadataCollection
            .findOneAndUpdate(
                WebNovelMetadata.byId(providerId, novelId),
                setValue(WebNovelMetadata::changeAt, LocalDateTime.now()),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
    }

    private suspend fun syncEs(
        novel: WebNovelMetadata,
        hasChange: Boolean,
    ) {
        if (hasChange) {
            es.client.indexDocument(
                id = "${novel.providerId}.${novel.novelId}",
                target = ElasticSearchDataSource.webNovelIndexName,
                document = WebNovelMetadataEsModel(
                    providerId = novel.providerId,
                    novelId = novel.novelId,
                    titleJp = novel.titleJp,
                    titleZh = novel.titleZh,
                    authors = novel.authors.map { it.name },
                    changeAt = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
                ),
                refresh = Refresh.WaitFor,
            )
        } else {
            @Serializable
            data class EsNovelUpdate(val titleJp: String, val titleZh: String?, val authors: List<String>)

            val update = EsNovelUpdate(novel.titleJp, novel.titleZh, novel.authors.map { it.name })
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
    titleZh: String?,
) =
    WebNovelMetadataOutline(
        providerId = providerId,
        novelId = novelId,
        titleJp = title,
        titleZh = titleZh,
        extra = meta,
    )

private fun WebNovelMetadataEsModel.toOutline() =
    WebNovelMetadataOutline(
        providerId = providerId,
        novelId = novelId,
        titleJp = titleJp,
        titleZh = titleZh,
        extra = null,
    )