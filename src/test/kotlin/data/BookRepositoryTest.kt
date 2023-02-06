package data

import api.*
import data.file.BookFileLang
import data.file.BookFileRepository
import data.file.BookFileType
import data.provider.ProviderDataSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest

val appModule = module {
    single { MongoDataSource("mongodb://192.168.1.100:27017") }
    single { ProviderDataSource() }

    single { BookMetadataRepository(get(), get()) }
    single { BookEpisodeRepository(get(), get(), get()) }
    single { BookPatchRepository(get(), get(), get()) }
    single { BookFileRepository() }

    single { PrepareBookService(get(), get(), get()) }
    single { NovelService(get(), get()) }
    single { PatchService(get()) }
    single { UpdateJpService(get(), get()) }
    single { UpdateZhService(get(), get()) }
}

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val service by inject<PrepareBookService>(PrepareBookService::class.java)

    init {
        describe("test") {
            val providerId = "pixiv"
            val bookId = "9001702"
            service.updateBookFile(providerId, bookId, BookFileLang.MIX, BookFileType.EPUB)
        }
    }

}
