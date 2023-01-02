package data.provider

import data.provider.providers.*

object ProviderRegister {
    private val providers = mapOf(
        Hameln.id to Hameln(),
        Kakuyomu.id to Kakuyomu(),
        Novelup.id to Novelup(),
        Syosetu.id to Syosetu(),
        Pixiv.id to Pixiv(),
    )

    fun getProvider(providerId: String): BookProvider? {
        return providers[providerId]
    }
}