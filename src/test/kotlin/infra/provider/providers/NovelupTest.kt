package infra.provider.providers

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.Instant

class NovelupTest : DescribeSpec({
    val provider = Novelup()

    describe("getMetadata") {
        it("常规") {
            // https://novelup.plus/story/206612087
            val metadata = provider.getMetadata("206612087")
            metadata.title.shouldBe("クロの戦記　異世界転移した僕が最強なのはベッドの上だけのようです")
            metadata.authors.first().name.shouldBe("サイトウアユム")
            metadata.authors.first().link.shouldBe("https://novelup.plus/user/930309375/profile")
            metadata.introduction.shouldStartWith("ケフェウス帝国の貴族であるクロノ・クロフォードには秘密があった。")
            metadata.introduction.shouldEndWith("小説家になろう様にマルチ投稿中。")
            metadata.toc[0].title.shouldBe("第１部：立志編")
            metadata.toc[0].chapterId.shouldBeNull()
            metadata.toc[0].createAt.shouldBeNull()
            metadata.toc[1].title.shouldBe("001　第１話：クロノ")
            metadata.toc[1].chapterId.shouldBe("614254159")
            metadata.toc[1].createAt.shouldBe(Instant.parse("2019-05-17T09:43:00Z"))
        }
        it("常规，单页") {
            // https://novelup.plus/story/358276052
            val metadata = provider.getMetadata("358276052")
            metadata.toc.size.shouldBe(1)
        }
        it("常规，无标签") {
            // https://novelup.plus/story/140197887
            val metadata = provider.getMetadata("140197887")
            metadata.keywords.shouldBeEmpty()
        }
    }

    describe("getChapter") {
        it("常规，有罗马音") {
            // https://novelup.plus/story/206612087/614254159
            val chapter = provider.getChapter("206612087", "614254159")
            chapter.paragraphs[6].shouldBe("　ケフェウス帝国と神聖アルゴ王国の国境に広がる原生林は昏き森と呼ばれている。")
        }
    }
})
