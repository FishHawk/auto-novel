package data.web

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import data.ElasticSearchDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class EsBookPageList(
    val items: List<NovelMetadata>,
    val total: Long,
)

@Serializable
data class NovelMetadata(
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<String>,
    val changeAt: Long,
)

class WebNovelIndexRepository(
    es: ElasticSearchDataSource,
) {
    private val client = es.client
    private val indexName = "metadata"

    init {
        runBlocking {
            runCatching {
                client.createIndex(indexName) {
                    mappings(dynamicEnabled = false) {
                        keyword(NovelMetadata::providerId)
                        text(NovelMetadata::titleJp) {
                            analyzer = "icu_analyzer"
                        }
                        text(NovelMetadata::titleZh) {
                            analyzer = "icu_analyzer"
                        }
                        keyword(NovelMetadata::authors)
                        date(NovelMetadata::changeAt)
                    }
                }
            }
        }
    }

    suspend fun index(
        providerId: String,
        novelId: String,
        titleJp: String,
        titleZh: String?,
        authors: List<String>,
        changeAt: Long,
    ) {
        client.indexDocument(
            id = "${providerId}.${novelId}",
            target = indexName,
            document = NovelMetadata(
                providerId = providerId,
                novelId = novelId,
                titleJp = titleJp,
                titleZh = titleZh,
                authors = authors,
                changeAt = changeAt,
            ),
            refresh = Refresh.WaitFor,
        )
    }

    @Serializable
    private data class EsBookMetadataUpdate(
        val titleJp: String,
        val titleZh: String?,
        val authors: List<String>,
    )

    suspend fun update(
        providerId: String,
        novelId: String,
        titleJp: String,
        titleZh: String?,
        authors: List<String>,
    ) {
        client.updateDocument(
            target = indexName,
            id = "${providerId}.${novelId}",
            docJson = Json.encodeToString(EsBookMetadataUpdate(titleJp, titleZh, authors)),
            refresh = Refresh.WaitFor,
        )
    }

    suspend fun search(
        queryString: String?,
        providerId: String?,
        page: Int,
        pageSize: Int,
    ): EsBookPageList {
        return client.search(
            indexName,
            from = page * pageSize,
            size = pageSize
        ) {
            query = bool {
                if (providerId != null) {
                    filter(
                        term(NovelMetadata::providerId, providerId)
                    )
                }
                if (queryString != null) {
                    must(
                        disMax {
                            queries(
                                match(NovelMetadata::titleJp, queryString),
                                match(NovelMetadata::titleZh, queryString),
                                match(NovelMetadata::authors, queryString),
                            )
                        }
                    )
                } else {
                    sort {
                        add(NovelMetadata::changeAt)
                    }
                }
            }
        }.let {
            EsBookPageList(
                items = it.hits?.hits?.map { hit -> hit.parseHit() } ?: emptyList(),
                total = it.total,
            )
        }
    }

    suspend fun addBunch(
        list: List<NovelMetadata>,
    ) {
        client.bulk {
            list.forEach {
                index(
                    doc = it,
                    id = "${it.providerId}.${it.novelId}",
                    index = "metadata",
                )
            }
        }
    }
}