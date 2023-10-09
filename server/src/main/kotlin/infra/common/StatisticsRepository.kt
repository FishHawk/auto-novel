package infra.common

import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.updateDocument
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import infra.DataSourceElasticSearch
import infra.DataSourceMongo
import infra.DataSourceRedis
import infra.WebNovelMetadataEsModel
import infra.model.WebNovelMetadata
import infra.model.WenkuNovelMetadata
import io.github.crackthecodeabhi.kreds.args.SetOption
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.litote.kmongo.inc
import kotlin.time.Duration.Companion.hours

class StatisticsRepository(
    private val mongo: DataSourceMongo,
    private val es: DataSourceElasticSearch,
    private val redis: DataSourceRedis,
) {
    suspend fun increaseWebNovelVisited(
        usernameOrIp: String,
        providerId: String,
        novelId: String,
    ) = applyRecord("sr:web-visited:${usernameOrIp}:${providerId}:${novelId}") {
        val novel = mongo
            .webNovelMetadataCollection
            .findOneAndUpdate(
                WebNovelMetadata.byId(providerId, novelId),
                inc(WebNovelMetadata::visited, 1),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            ) ?: return
        es.client.updateDocument(
            id = "${novel.providerId}.${novel.novelId}",
            target = DataSourceElasticSearch.webNovelIndexName,
            doc = buildJsonObject {
                put(
                    WebNovelMetadataEsModel::visited.name,
                    novel.visited,
                )
            },
            refresh = Refresh.WaitFor,
        )
    }

    suspend fun increaseWenkuNovelVisited(
        usernameOrIp: String,
        novelId: String,
    ) = applyRecord("sr:wenku-visited:${usernameOrIp}:${novelId}") {
        mongo
            .wenkuNovelMetadataCollection
            .updateOne(
                WenkuNovelMetadata.byId(novelId),
                inc(WenkuNovelMetadata::visited, 1),
            )
    }

    private suspend inline fun applyRecord(key: String, block: () -> Unit) {
        if (redis.exists(key) == 0L) {
            redis.set(
                key = key,
                value = "0",
                setOption = SetOption.Builder()
                    .exSeconds(3.hours.inWholeSeconds.toULong())
                    .build(),
            )
            block()
        }
    }
}