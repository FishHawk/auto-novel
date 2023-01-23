package data.provider

import data.provider.providers.*

class ProviderDataSource {
    companion object {
        private val providers = mapOf(
            Hameln.id to Hameln(),
            Kakuyomu.id to Kakuyomu(),
            Novelup.id to Novelup(),
            Syosetu.id to Syosetu(),
            Pixiv.id to Pixiv(),
        )
    }

    suspend fun getMetadata(providerId: String, bookId: String): Result<SBookMetadata> {
        return runCatching {
            providers[providerId]!!.getMetadata(bookId)
        }
    }

    suspend fun getEpisode(providerId: String, bookId: String, episodeId: String): Result<SBookEpisode> {
        return runCatching {
            providers[providerId]!!.getEpisode(bookId, episodeId)
        }
    }
}