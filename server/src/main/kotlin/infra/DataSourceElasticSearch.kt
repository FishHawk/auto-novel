package infra

import com.jillesvangurp.ktsearch.*
import domain.entity.WebNovelAttention
import domain.entity.WebNovelType
import domain.entity.WenkuNovelLevel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
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
    val visited: Int,
    val hasGpt: Boolean,
    val hasSakura: Boolean,
    val updateAt: Long,
)

@Serializable
data class WenkuNovelMetadataEsModel(
    val id: String,
    val title: String,
    val titleZh: String,
    val cover: String?,
    val authors: List<String>,
    val artists: List<String>,
    val keywords: List<String>,
    val publisher: String?,
    val imprint: String?,
    @Contextual val latestPublishAt: Instant?,
    val level: WenkuNovelLevel,
    @Contextual val updateAt: Instant,
)

class DataSourceElasticSearch(host: String, port: Int?) {
    val client = SearchClient(
        KtorRestClient(
            Node(host, port ?: 9200)
        )
    )

    companion object {
        const val webNovelIndexName = "web-index-alt"
        const val wenkuNovelIndexName = "wenku.2024-05-15"
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
                        number<Int>(WebNovelMetadataEsModel::visited)
                        bool(WebNovelMetadataEsModel::hasGpt)
                        bool(WebNovelMetadataEsModel::hasSakura)
                        date(WebNovelMetadataEsModel::updateAt)
                    }
                }
            }
        }
    }
}