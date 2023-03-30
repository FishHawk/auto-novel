package data

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient

class ElasticSearchDataSource(url: String) {
    val client = SearchClient(
        KtorRestClient(
            Node(url, 9200)
        )
    )
}