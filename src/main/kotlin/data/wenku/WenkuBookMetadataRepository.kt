package data.wenku

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.disMax
import com.jillesvangurp.searchdsls.querydsl.match
import com.jillesvangurp.searchdsls.querydsl.sort
import data.ElasticSearchDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.litote.kmongo.limit

class WenkuBookMetadataRepository(
    private val es: ElasticSearchDataSource,
) {
    @Serializable
    data class Metadata(
        val bookId: String,
        val title: String,
        val cover: String,
        val coverSmall: String,
        val author: String,
        val artist: String,
        val keywords: List<String>,
        val introduction: String,
        val updateAt: Long,
    )

    data class MetadataPageList(
        val items: List<Metadata>,
        val total: Long,
    )

    private val indexName = "wenku-metadata"

    init {
        runBlocking {
            runCatching {
                es.client.createIndex(indexName) {
                    mappings(dynamicEnabled = false) {
                        keyword(Metadata::keywords)
                        text(Metadata::title) { analyzer = "icu_analyzer" }
                        keyword(Metadata::author)
                        keyword(Metadata::artist)
                        date(Metadata::updateAt)
                    }
                }
            }.let { println(it) }
        }
    }

    suspend fun get(
        bangumiId: String,
    ): Metadata? {
        return runCatching {
            es.client.getDocument(
                id = bangumiId,
                target = indexName,
            ).document<Metadata>()
        }.getOrNull()
    }

    suspend fun index(
        metadata: Metadata,
    ) {
        es.client.indexDocument(
            id = metadata.bookId,
            target = indexName,
            document = metadata,
            refresh = Refresh.WaitFor,
        )
    }

    suspend fun search(
        queryString: String?,
        page: Int,
        pageSize: Int,
    ): MetadataPageList {
        return es.client.search(indexName) {
            query = bool {
                from = page * pageSize
                limit(pageSize)
                if (queryString != null) {
                    must(
                        disMax {
                            queries(
                                match(Metadata::title, queryString),
                                match(Metadata::author, queryString),
                                match(Metadata::artist, queryString),
                                match(Metadata::keywords, queryString),
                            )
                        }
                    )
                } else {
                    sort {
                        add(Metadata::updateAt)
                    }
                }
            }
        }.let {
            MetadataPageList(
                items = it.hits?.hits?.map { hit -> hit.parseHit() } ?: emptyList(),
                total = it.total,
            )
        }
    }
}