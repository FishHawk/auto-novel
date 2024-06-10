package infra

import com.jillesvangurp.ktsearch.*
import domain.entity.WebNovelAttention
import domain.entity.WebNovelType
import domain.entity.WenkuNovelLevel
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
    @Contextual val updateAt: Instant,
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
        const val webNovelIndexName = "web.2024-06-10"
        const val wenkuNovelIndexName = "wenku.2024-05-15"
    }
}