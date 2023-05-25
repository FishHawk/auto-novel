package infra.wenku

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.disMax
import com.jillesvangurp.searchdsls.querydsl.match
import com.jillesvangurp.searchdsls.querydsl.sort
import infra.ElasticSearchDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

class WenkuNovelIndexRepository(
    private val es: ElasticSearchDataSource,
) {
    @Serializable
    data class Novel(
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

    data class NovelPage(
        val items: List<Novel>,
        val total: Long,
    )

    private val indexName = "wenku-index"

    init {
        runBlocking {
            runCatching {
                es.client.createIndex(indexName) {
                    mappings(dynamicEnabled = false) {
                        text(Novel::title) { analyzer = "icu_analyzer" }
                        text(Novel::titleZh) { analyzer = "icu_analyzer" }
                        text(Novel::titleZhAlias) { analyzer = "icu_analyzer" }
                        keyword(Novel::authors)
                        keyword(Novel::artists)
                        keyword(Novel::keywords)
                        date(Novel::updateAt)
                    }
                }
            }
        }
    }

    suspend fun index(
        novelId: String,
        title: String,
        titleZh: String,
        titleZhAlias: List<String>,
        cover: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
    ) {
        es.client.indexDocument(
            id = novelId,
            target = indexName,
            document = Novel(
                id = novelId,
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
    ): NovelPage {
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
                                match(Novel::title, queryString),
                                match(Novel::titleZh, queryString),
                                match(Novel::titleZhAlias, queryString),
                                match(Novel::authors, queryString),
                                match(Novel::artists, queryString),
                                match(Novel::keywords, queryString),
                            )
                        }
                    )
                } else {
                    sort {
                        add(Novel::updateAt)
                    }
                }
            }
        }.let {
            NovelPage(
                items = it.hits?.hits?.map { hit -> hit.parseHit() } ?: emptyList(),
                total = it.total,
            )
        }
    }

    suspend fun addBunch(
        list: List<Novel>,
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