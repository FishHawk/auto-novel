package infra.provider.providers

import koinExtensions
import infra.web.datasource.WebNovelHttpDataSource
import infra.web.datasource.providers.Alphapolis
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.koin.test.KoinTest
import org.koin.test.inject

class AlphapolisTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val dataSource by inject<WebNovelHttpDataSource>()
    private val provider get() = dataSource.providers[Alphapolis.id]!!

    init {
        describe("getMetadata") {
            it("常规") {
                // https://www.alphapolis.co.jp/novel/638978238/525733370
                val metadata = provider.getMetadata("638978238-525733370")
                metadata.title.shouldStartWith("今までの功績を改竄され")
                metadata.authors.first().name.shouldBe("taki210")
                metadata.authors.first().link.shouldBe("https://www.alphapolis.co.jp/author/detail/638978238")
                metadata.introduction.shouldStartWith("「今日限りでお前をこの")
                metadata.introduction.shouldEndWith("っていたのだった。")
                metadata.toc[0].title.shouldBe("第一話")
                metadata.toc[0].chapterId.shouldBe("6857738")
            }
            it("折叠") {
                // https://www.alphapolis.co.jp/novel/761693105/571330821
                provider.getMetadata("761693105-571330821")
            }
            it("R18") {
                // https://www.alphapolis.co.jp/novel/770037297/275621537
                provider.getMetadata("770037297-275621537")
            }
        }

        describe("getEpisode") {
            it("常规") {
                // https://www.alphapolis.co.jp/novel/638978238/525733370/episode/6857739
                val episode = provider.getChapter("638978238-525733370", "6857739")
                episode.paragraphs.getOrNull(1).shouldBe("「これからどうすっかなぁ…」")
            }
            it("ruby标签") {
                // https://www.alphapolis.co.jp/novel/606793319/240579102/episode/5375163
                val episode = provider.getChapter("606793319-240579102", "5375163")
                episode.paragraphs.first().shouldBe("海斗の携帯は瑛人の顔に当たり、地面に落ちる")
            }
        }
    }
}
