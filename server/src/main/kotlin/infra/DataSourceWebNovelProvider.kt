package infra

import infra.provider.RemoteChapter
import infra.provider.RemoteNovelListItem
import infra.provider.RemoteNovelMetadata
import infra.provider.providers.*

class DataSourceWebNovelProvider {
    companion object {
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

    suspend fun listRank(providerId: String, options: Map<String, String>): Result<List<RemoteNovelListItem>> {
        return runCatching {
            providers[providerId]!!.getRank(options)
        }
    }

    suspend fun getMetadata(providerId: String, novelId: String): Result<RemoteNovelMetadata> {
        return runCatching {
            providers[providerId]!!.getMetadata(novelId)
        }
    }

    suspend fun getChapter(providerId: String, novelId: String, chapterId: String): Result<RemoteChapter> {
        return runCatching {
            providers[providerId]!!.getChapter(novelId, chapterId)
        }
    }
}