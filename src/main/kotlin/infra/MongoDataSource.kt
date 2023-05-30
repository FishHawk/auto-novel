package infra

import infra.model.WebNovelChapter
import infra.model.WebNovelMetadata
import infra.model.WebNovelPatchHistory
import infra.model.WebNovelTocMergeHistory
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

class MongoDataSource(url: String) {
    val client = KMongo.createClient(url).coroutine
    val database = client.getDatabase("main")

    val webNovelMetadataCollection
        get() = database.getCollection<WebNovelMetadata>("metadata")

    init {
        runBlocking {
            webNovelMetadataCollection.ensureUniqueIndex(
                WebNovelMetadata::providerId,
                WebNovelMetadata::novelId,
            )
        }
    }

    val webNovelChapterCollection
        get() = database.getCollection<WebNovelChapter>("episode")

    init {
        runBlocking {
            webNovelChapterCollection.ensureUniqueIndex(
                WebNovelChapter::providerId,
                WebNovelChapter::novelId,
                WebNovelChapter::chapterId,
            )
        }
    }

    val webNovelPatchHistoryCollection
        get() = database.getCollection<WebNovelPatchHistory>("web-patch")

    init {
        runBlocking {
            webNovelPatchHistoryCollection.ensureUniqueIndex(
                WebNovelPatchHistory::providerId,
                WebNovelPatchHistory::novelId,
            )
        }
    }

    val webNovelTocMergeHistoryCollection
        get() = database.getCollection<WebNovelTocMergeHistory>("toc-merge-history")
}