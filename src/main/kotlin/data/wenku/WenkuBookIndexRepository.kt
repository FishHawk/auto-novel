package data.wenku

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.disMax
import com.jillesvangurp.searchdsls.querydsl.match
import com.jillesvangurp.searchdsls.querydsl.sort
import data.ElasticSearchDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

class WenkuBookIndexRepository(
    private val es: ElasticSearchDataSource,
) {
    @Serializable
    data class BookDocument(
        val id: String,
        val title: String,
        val titleZh: String,
        val titleZhAlias: List<String>,
        val cover: String,
        val authors: List<String>,
        val artists: List<String>,
        val keywords: List<String>,
        val updateAt: Long,
    )

    data class BookPage(
        val items: List<BookDocument>,
        val total: Long,
    )

    private val indexName = "wenku-index"

    init {
        runBlocking {
            runCatching {
                es.client.createIndex(indexName) {
                    mappings(dynamicEnabled = false) {
                        text(BookDocument::title) { analyzer = "icu_analyzer" }
                        text(BookDocument::titleZh) { analyzer = "icu_analyzer" }
                        text(BookDocument::titleZhAlias) { analyzer = "icu_analyzer" }
                        keyword(BookDocument::authors)
                        keyword(BookDocument::artists)
                        keyword(BookDocument::keywords)
                        date(BookDocument::updateAt)
                    }
                }
            }
        }
    }

    suspend fun index(
        id: String,
        title: String,
        titleZh: String,
        titleZhAlias: List<String>,
        cover: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
    ) {
        es.client.indexDocument(
            id = id,
            target = indexName,
            document = BookDocument(
                id = id,
                title = title,
                titleZh = titleZh,
                titleZhAlias = titleZhAlias,
                cover = cover,
                authors = authors,
                artists = artists,
                keywords = keywords,
                updateAt = Clock.System.now().epochSeconds
            ),
            refresh = Refresh.WaitFor,
        )
    }

    suspend fun search(
        queryString: String?,
        page: Int,
        pageSize: Int,
    ): BookPage {
        return es.client.search(
            indexName,
            from = page * pageSize,
            size = pageSize
        ) {
            query = bool {
                if (queryString != null) {
                    must(
                        disMax {
                            queries(
                                match(BookDocument::title, queryString),
                                match(BookDocument::titleZh, queryString),
                                match(BookDocument::titleZhAlias, queryString),
                                match(BookDocument::authors, queryString),
                                match(BookDocument::artists, queryString),
                                match(BookDocument::keywords, queryString),
                            )
                        }
                    )
                } else {
                    sort {
                        add(BookDocument::updateAt)
                    }
                }
            }
        }.let {
            BookPage(
                items = it.hits?.hits?.map { hit -> hit.parseHit() } ?: emptyList(),
                total = it.total,
            )
        }
    }

    suspend fun addBunch(
        list: List<BookDocument>,
    ) {
        es.client.bulk {
            list.forEach {
                index(
                    id = it.id,
                    doc = it,
                    index = indexName,
                )
            }
        }
    }
}