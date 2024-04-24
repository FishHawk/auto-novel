package infra.provider.providers

import infra.client
import infra.web.providers.Syosetu
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.Instant

class SyosetuTest : DescribeSpec({
    val provider = Syosetu(client)

    describe("getRank") {
        it("常规") {
            shouldNotThrow<Throwable> {
                provider.getRank(
                    mapOf(
                        "type" to "流派",
                        "genre" to "恋爱：异世界",
                        "range" to "每月",
                    )
                )
            }
        }
    }

    describe("getMetadata") {
        it("常规") {
            // https://ncode.syosetu.com/n9669bk
            val metadata = provider.getMetadata("n9669bk")
            metadata.title.shouldBe("無職転生　- 異世界行ったら本気だす -")
            metadata.authors.first().name.shouldBe("理不尽な孫の手")
            metadata.authors.first().link.shouldBe("https://mypage.syosetu.com/288399/")
            metadata.introduction.shouldStartWith("３４歳職歴無し住所不定無職童貞のニートは")
            metadata.introduction.shouldEndWith("http://ncode.syosetu.com/n4251cr/")
            metadata.toc[0].title.shouldBe("第１章　幼年期")
            metadata.toc[0].chapterId.shouldBeNull()
            metadata.toc[0].createAt.shouldBeNull()
            metadata.toc[1].title.shouldBe("プロローグ")
            metadata.toc[1].chapterId.shouldBe("1")
            metadata.toc[1].createAt.shouldBe(Instant.parse("2012-11-22T08:00:00Z"))
        }
        it("常规，作者无链接") {
            // https://ncode.syosetu.com/n0123fj
            val metadata = provider.getMetadata("n0123fj")
            metadata.authors.first().link.shouldBeNull()
        }
        it("常规，简介需要展开，但不需要处理") {
            // https://ncode.syosetu.com/n8473hv
            val metadata = provider.getMetadata("n8473hv")
            metadata.introduction.shouldEndWith("カクヨム様でも掲載中です。")
        }
        it("常规，R18重定向") {
            // https://ncode.syosetu.com/n5305eg
            val metadata = provider.getMetadata("n5305eg")
            metadata.title.shouldBe("【web版】エロいスキルで異世界無双")
        }
        it("常规，検索除外中です") {
            // https://ncode.syosetu.com/n8539hm
            val metadata = provider.getMetadata("n8539hm")
            metadata.points.shouldBeNull()
        }
        it("短篇") {
            // https://ncode.syosetu.com/n0916hw
            val metadata = provider.getMetadata("n0916hw")
            metadata.toc.size.shouldBe(1)
        }
    }

    describe("getEpisode") {
        it("常规") {
            // https://ncode.syosetu.com/n9669bk/1
            val episode = provider.getChapter("n9669bk", "1")
            episode.paragraphs.first().shouldBe("　俺は34歳住所不定無職。")
            episode.paragraphs.last().shouldBe("　俺はトラックとコンクリートに挟まれて、トマトみたいに潰れて死んだ。")
        }
        it("常规，R18重定向") {
            // https://ncode.syosetu.com/n5305eg/1
            val episode = provider.getChapter("n5305eg", "1")
            episode.paragraphs.first().shouldBe("　「なんか、面白いことねーかなー」")
            episode.paragraphs.last().shouldBe("　プレイ確定！")
        }
        it("短篇") {
            // https://ncode.syosetu.com/n0916hw
            val episode = provider.getChapter("n0916hw", "default")
            episode.paragraphs.first().shouldBe("")
            episode.paragraphs.last().shouldBe("")
        }
        it("ruby标签过滤") {
            // https://ncode.syosetu.com/n2907ga/74
            val episode = provider.getChapter("n2907ga", "74")
            episode.paragraphs[17].shouldEndWith("皮肉にも人の理の外にあるものだったのか」")
            episode.paragraphs[29].shouldBe("「私が思うのと同程度に、彼女があなたにとって大事な存在になっただって？　―――あり得ない話だ」")
        }
        it("插图") {
            // https://ncode.syosetu.com/n9025bp/55
            val episode = provider.getChapter("n9025bp", "55")
            episode.paragraphs[0].shouldStartWith("<图片>")
        }
    }
})
