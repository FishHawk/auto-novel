package infra

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient

typealias ElasticSearchClient = SearchClient

fun elasticSearchClient(host: String, port: Int?) =
    SearchClient(
        KtorRestClient(
            Node(host, port ?: 9200),
        )
    )

object ElasticSearchIndexNames {
    const val WEB_NOVEL = "web.2024-06-10"
    const val WENKU_NOVEL = "wenku.2024-05-15"
}