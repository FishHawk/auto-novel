package data.web

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import data.ElasticSearchDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class EsBookPageList(
    val items: List<EsBookMetadata>,
    val total: Long,
)

@Serializable
data class EsBookMetadata(
    val providerId: String,
    val bookId: String,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<String>,
    val changeAt: Long,
)

class WebBookIndexRepository(
    source: ElasticSearchDataSource,
) {
    private val client = source.client
    private val indexName = "metadata"

    init {
        runBlocking {
            runCatching {
                client.createIndex(indexName) {
                    mappings(dynamicEnabled = false) {
                        keyword(EsBookMetadata::providerId)
                        text(EsBookMetadata::titleJp) {
                            analyzer = "icu_analyzer"
                        }
                        text(EsBookMetadata::titleZh) {
                            analyzer = "icu_analyzer"
                        }
                        keyword(EsBookMetadata::authors)
                        date(EsBookMetadata::changeAt)
                    }
                }
            }
        }
    }

    suspend fun index(
        providerId: String,
        bookId: String,
        titleJp: String,
        titleZh: String?,
        authors: List<String>,
        changeAt: Long,
    ) {
        client.indexDocument(
            id = "${providerId}.${bookId}",
            target = indexName,
            document = EsBookMetadata(
                providerId = providerId,
                bookId = bookId,
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
        bookId: String,
        titleJp: String,
        titleZh: String?,
        authors: List<String>,
    ) {
        client.updateDocument(
            target = indexName,
            id = "${providerId}.${bookId}",
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
                        term(EsBookMetadata::providerId, providerId)
                    )
                }
                if (queryString != null) {
                    must(
                        disMax {
                            queries(
                                match(EsBookMetadata::titleJp, queryString),
                                match(EsBookMetadata::titleZh, queryString),
                                match(EsBookMetadata::authors, queryString),
                            )
                        }
                    )
                } else {
                    sort {
                        add(EsBookMetadata::changeAt)
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
        list: List<EsBookMetadata>,
    ) {
        client.bulk {
            list.forEach {
                index(
                    doc = it,
                    id = "${it.providerId}.${it.bookId}",
                    index = "metadata",
                )
            }
        }
    }
}