package data.web

import data.provider.providers.Hameln
import data.provider.providers.Syosetu

fun isProviderIdUnstable(providerId: String): Boolean {
    return providerId == Syosetu.id || providerId == Hameln.id
}

data class MergedResult(
    val toc: List<BookTocItem>,
    val hasChanged: Boolean,
    val reviewReason: String?,
)

fun mergeToc(
    remoteToc: List<BookTocItem>,
    localToc: List<BookTocItem>,
    isIdUnstable: Boolean,
): MergedResult {
    return if (isIdUnstable) {
        mergeTocUnstable(remoteToc, localToc)
    } else {
        mergeTocStable(remoteToc, localToc)
    }
}

private fun mergeTocUnstable(
    remoteToc: List<BookTocItem>,
    localToc: List<BookTocItem>,
): MergedResult {
    val remoteIdToTitle = remoteToc.mapNotNull {
        if (it.episodeId == null) null
        else it.episodeId to it.titleJp
    }.toMap()
    val localIdToTitle = localToc.mapNotNull {
        if (it.episodeId == null) null
        else it.episodeId to it.titleJp
    }.toMap()

    if (remoteIdToTitle.size < localIdToTitle.size) {
        return MergedResult(
            simpleMergeToc(remoteToc, localToc),
            true,
            "有未知章节被删了"
        )
    } else {
        val hasEpisodeTitleChanged = localIdToTitle.any { (eid, localTitle) ->
            val remoteTitle = remoteIdToTitle[eid]
            remoteTitle != localTitle
        }
        return MergedResult(
            simpleMergeToc(remoteToc, localToc),
            remoteIdToTitle.size != localIdToTitle.size,
            if (hasEpisodeTitleChanged) "有章节标题变化" else null
        )
    }
}

private fun mergeTocStable(
    remoteToc: List<BookTocItem>,
    localToc: List<BookTocItem>,
): MergedResult {
    val remoteEpIds = remoteToc.mapNotNull { it.episodeId }
    val localEpIds = localToc.mapNotNull { it.episodeId }
    val noEpDeleted = remoteEpIds.containsAll(localEpIds)
    val noEpAdded = localEpIds.containsAll(remoteEpIds)
    return MergedResult(
        simpleMergeToc(remoteToc, localToc),
        !(noEpAdded && noEpDeleted),
        if (noEpDeleted) null else "有章节被删了"
    )
}

private fun simpleMergeToc(
    remoteToc: List<BookTocItem>,
    localToc: List<BookTocItem>,
): List<BookTocItem> {
    return remoteToc.map { itemNew ->
        val itemOld = localToc.find { it.titleJp == itemNew.titleJp }
        if (itemOld?.titleZh == null) {
            itemNew
        } else {
            itemNew.copy(titleZh = itemOld.titleZh)
        }
    }
}
