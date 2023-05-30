package infra

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.createIndex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebNovelMetadataEsModel(
    val providerId: String,
    @SerialName("bookId")
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<String>,
    val changeAt: Long,
)

class ElasticSearchDataSource(url: String) {
    val client = SearchClient(
        KtorRestClient(
            Node(url, 9200)
        )
    )

    companion object {
        const val webNovelIndexName = "metadata"
    }

    init {
        runBlocking {
            runCatching {
                client.createIndex(webNovelIndexName) {
                    mappings(dynamicEnabled = false) {
                        keyword(WebNovelMetadataEsModel::providerId)
                        text(WebNovelMetadataEsModel::titleJp) {
                            analyzer = "icu_analyzer"
                        }
                        text(WebNovelMetadataEsModel::titleZh) {
                            analyzer = "icu_analyzer"
                        }
                        keyword(WebNovelMetadataEsModel::authors)
                        date(WebNovelMetadataEsModel::changeAt)
                    }
                }
            }
        }
    }
}