package domain

import infra.model.WebNovelChapter
import infra.model.WebNovelMetadata
import infra.model.WebNovelTocItem
import infra.provider.providers.Hameln
import infra.provider.providers.Syosetu
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelMetadataRepository
import infra.web.WebNovelTocMergeHistoryRepository
import java.lang.RuntimeException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class WebNovelUpdateService(
    private val novelRepo: WebNovelMetadataRepository,
    private val chapterRepo: WebNovelChapterRepository,
    private val tocMergeHistoryRepo: WebNovelTocMergeHistoryRepository,
) {
    suspend fun getNovelAndSave(
        providerId: String,
        novelId: String,
        expiredMinutes: Int = 20 * 60,
    ): Result<WebNovelMetadata> {
        // 不在数据库中
        val novelLocal = novelRepo.get(providerId, novelId)
            ?: return novelRepo.getRemoteAndSave(providerId, novelId)

        // 在数据库中，暂停更新
        if (novelLocal.pauseUpdate) {
            return Result.success(novelLocal)
        }

        // 在数据库中，没有过期
        val minutes = ChronoUnit.MINUTES.between(novelLocal.syncAt, LocalDateTime.now())
        val isExpired = minutes > expiredMinutes
        if (!isExpired) {
            return Result.success(novelLocal)
        }

        // 在数据库中，过期，合并
        val remoteNovel = novelRepo.getRemote(providerId, novelId)
            .getOrElse {
                // 无法更新，大概率小说被删了
                return Result.success(novelLocal)
            }
        val merged = mergeNovel(
            providerId = providerId,
            novelId = novelId,
            novelLocal = novelLocal,
            novelRemote = remoteNovel,
        )
        return Result.success(merged)
    }

    private suspend fun mergeNovel(
        providerId: String,
        novelId: String,
        novelLocal: WebNovelMetadata,
        novelRemote: WebNovelMetadata,
    ): WebNovelMetadata {
        val merged = mergeToc(
            remoteToc = novelRemote.toc,
            localToc = novelLocal.toc,
            isIdUnstable = isProviderIdUnstable(providerId)
        )
        if (merged.reviewReason != null) {
            tocMergeHistoryRepo.insert(
                providerId = providerId,
                novelId = novelId,
                tocOld = novelLocal.toc,
                tocNew = novelRemote.toc,
                reason = merged.reviewReason,
            )
        }

        return novelRepo.update(
            providerId = providerId,
            novelId = novelId,
            titleJp = novelRemote.titleJp,
            type = novelRemote.type,
            attentions = novelRemote.attentions,
            keywords = novelRemote.keywords,
            introductionJp = novelRemote.introductionJp,
            toc = merged.toc,
            hasChanged = merged.hasChanged,
        ) ?: throw RuntimeException("更新网络小说时，小说不存在。不应当触发。")
    }

    suspend fun getChapterAndSave(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): Result<WebNovelChapter> {
        val chapterLocal = chapterRepo.get(providerId, novelId, chapterId)
        if (chapterLocal != null) return Result.success(chapterLocal)

        return chapterRepo.getRemoteAndSave(providerId, novelId, chapterId)
            .onSuccess { novelRepo.updateTranslateStateJp(providerId, novelId) }
    }
}


fun isProviderIdUnstable(providerId: String): Boolean {
    return providerId == Syosetu.id || providerId == Hameln.id
}

data class MergedResult(
    val toc: List<WebNovelTocItem>,
    val hasChanged: Boolean,
    val reviewReason: String?,
)

fun mergeToc(
    remoteToc: List<WebNovelTocItem>,
    localToc: List<WebNovelTocItem>,
    isIdUnstable: Boolean,
): MergedResult {
    return if (isIdUnstable) {
        mergeTocUnstable(remoteToc, localToc)
    } else {
        mergeTocStable(remoteToc, localToc)
    }
}

private fun mergeTocUnstable(
    remoteToc: List<WebNovelTocItem>,
    localToc: List<WebNovelTocItem>,
): MergedResult {
    val remoteIdToTitle = remoteToc.mapNotNull {
        if (it.chapterId == null) null
        else it.chapterId to it.titleJp
    }.toMap()
    val localIdToTitle = localToc.mapNotNull {
        if (it.chapterId == null) null
        else it.chapterId to it.titleJp
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
    remoteToc: List<WebNovelTocItem>,
    localToc: List<WebNovelTocItem>,
): MergedResult {
    val remoteEpIds = remoteToc.mapNotNull { it.chapterId }
    val localEpIds = localToc.mapNotNull { it.chapterId }
    val noEpDeleted = remoteEpIds.containsAll(localEpIds)
    val noEpAdded = localEpIds.containsAll(remoteEpIds)
    return MergedResult(
        simpleMergeToc(remoteToc, localToc),
        !(noEpAdded && noEpDeleted),
        if (noEpDeleted) null else "有章节被删了"
    )
}

private fun simpleMergeToc(
    remoteToc: List<WebNovelTocItem>,
    localToc: List<WebNovelTocItem>,
): List<WebNovelTocItem> {
    return remoteToc.map { itemNew ->
        val itemOld = localToc.find { it.titleJp == itemNew.titleJp }
        if (itemOld?.titleZh == null) {
            itemNew
        } else {
            itemNew.copy(titleZh = itemOld.titleZh)
        }
    }
}
