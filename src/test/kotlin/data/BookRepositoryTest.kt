package data

import appModule
import data.provider.ProviderDataSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.from
import org.litote.kmongo.util.KMongoUtil.toBson

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val repo by inject<ProviderDataSource>(ProviderDataSource::class.java)

    init {
        describe("test") {
            val bson = CommentView::downvote from toBson("{\$size: \"\$downvoteUsers\"}")
            println(bson.toBsonDocument())
        }
    }
}
