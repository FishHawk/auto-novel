package infra

import com.jillesvangurp.ktsearch.*
import infra.model.WebNovelAttention
import infra.model.WebNovelType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class WebNovelMetadataEsModel(
    val providerId: String,
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<String>,
    val type: WebNovelType = WebNovelType.连载中,
    val attentions: List<WebNovelAttention> = emptyList(),
    val keywords: List<String>,
    val tocSize: Int,
    val hasGpt: Boolean,
    val updateAt: Long,
)

@Serializable
data class WenkuNovelMetadataEsModel(
    val id: String,
    val title: String,
    val titleZh: String,
    val cover: String,
    val authors: List<String>,
    val artists: List<String>,
    val keywords: List<String>,
    val updateAt: Long,
)

class ElasticSearchDataSource(url: String) {
    val client = SearchClient(
        KtorRestClient(
            Node(url, 9200)
        )
    )

    companion object {
        const val webNovelIndexName = "web-index-alt"
        const val wenkuNovelIndexName = "wenku-index"
    }

    init {
        runBlocking {
            runCatching {
                client.createIndex(webNovelIndexName) {
                    mappings(dynamicEnabled = false) {
                        keyword(WebNovelMetadataEsModel::providerId)
                        text(WebNovelMetadataEsModel::titleJp) { analyzer = "icu_analyzer" }
                        text(WebNovelMetadataEsModel::titleZh) { analyzer = "icu_analyzer" }
                        keyword(WebNovelMetadataEsModel::authors)
                        keyword(WebNovelMetadataEsModel::type)
                        keyword(WebNovelMetadataEsModel::attentions)
                        keyword(WebNovelMetadataEsModel::keywords)
                        number<Int>(WebNovelMetadataEsModel::tocSize)
                        bool(WebNovelMetadataEsModel::hasGpt)
                        date(WebNovelMetadataEsModel::updateAt)
                    }
                }
            }

            runCatching {
                client.createIndex(wenkuNovelIndexName) {
                    mappings(dynamicEnabled = false) {
                        text(WenkuNovelMetadataEsModel::title) { analyzer = "icu_analyzer" }
                        text(WenkuNovelMetadataEsModel::titleZh) { analyzer = "icu_analyzer" }
                        keyword(WenkuNovelMetadataEsModel::authors)
                        keyword(WenkuNovelMetadataEsModel::artists)
                        keyword(WenkuNovelMetadataEsModel::keywords)
                        date(WenkuNovelMetadataEsModel::updateAt)
                    }
                }
            }
        }
    }
}