package data.web

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import data.ElasticSearchDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.litote.kmongo.limit

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

class EsBookMetadataRepository(
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

    suspend fun search(
        queryString: String?,
        providerId: String?,
        page: Int,
        pageSize: Int,
    ): EsBookPageList {
        return client.search(indexName) {
            query = bool {
                from = page * pageSize
                limit(pageSize)
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