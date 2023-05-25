package infra.provider

import infra.provider.providers.*
import org.slf4j.LoggerFactory

class WebNovelProviderDataSource {
    companion object {
        private val logger = LoggerFactory.getLogger(WebNovelProviderDataSource::class.java)
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

    suspend fun getRank(providerId: String, options: Map<String, String>): Result<List<RemoteNovelListItem>> {
        return runCatching {
            providers[providerId]!!.getRank(options)
        }.onFailure {
            logger.error("获取排行失败 $providerId/$options", it)
        }
    }

    suspend fun getMetadata(providerId: String, novelId: String): Result<RemoteNovelMetadata> {
        return runCatching {
            providers[providerId]!!.getMetadata(novelId)
        }.onFailure {
            logger.error("获取元数据失败 $providerId/$novelId", it)
        }
    }

    suspend fun getChapter(providerId: String, novelId: String, chapterId: String): Result<RemoteChapter> {
        return runCatching {
            providers[providerId]!!.getChapter(novelId, chapterId)
        }.onFailure {
            logger.error("获取章节失败 $providerId/$novelId/$chapterId", it)
        }
    }
}