package data

import api.*
import data.file.BookFileRepository
import data.provider.ProviderDataSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.div
import org.litote.kmongo.pos
import org.litote.kmongo.regex

val appModule = module {
    single { MongoDataSource("mongodb://192.168.1.110:27017") }
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

    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    init {
        describe("test") {
            val col = mongo.database.getCollection<BookMetadata>("metadata")
            val list = col.find(BookMetadata::authors.pos(0) / BookAuthor::name regex "^作者：.*$".toRegex()).toList()
            println(list.size)
        }
    }
}
