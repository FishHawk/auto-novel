package data.provider

import data.provider.providers.*
import org.slf4j.LoggerFactory

class ProviderDataSource {
    companion object {
        private val logger = LoggerFactory.getLogger(ProviderDataSource::class.java)
        private val providers = mapOf(
            Hameln.id to Hameln(),
            Kakuyomu.id to Kakuyomu(),
            Novelup.id to Novelup(),
            Syosetu.id to Syosetu(),
            Pixiv.id to Pixiv(),
            Alphapolis.id to Alphapolis(),
            Novelism.id to Novelism(),
        )
    }

    suspend fun getRank(providerId: String, options: Map<String, String>): Result<List<SBookListItem>> {
        return runCatching {
            providers[providerId]!!.getRank(options)
        }.onFailure {
            logger.error("获取排行失败 $providerId/$options", it)
        }
    }

    suspend fun getMetadata(providerId: String, bookId: String): Result<SBookMetadata> {
        return runCatching {
            providers[providerId]!!.getMetadata(bookId)
        }.onFailure {
            logger.error("获取元数据失败 $providerId/$bookId", it)
        }
    }

    suspend fun getEpisode(providerId: String, bookId: String, episodeId: String): Result<SBookEpisode> {
        return runCatching {
            providers[providerId]!!.getEpisode(bookId, episodeId)
        }.onFailure {
            logger.error("获取章节失败 $providerId/$bookId/$episodeId", it)
        }
    }
}