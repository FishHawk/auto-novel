package data.provider.providers

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith

class SyosetuTest : DescribeSpec({
    val provider = Syosetu()

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
            metadata.toc[0].episodeId.shouldBeNull()
            metadata.toc[1].title.shouldBe("プロローグ")
            metadata.toc[1].episodeId.shouldBe("1")
        }
        it("常规，简介需要展开，但不需要处理") {
            // https://ncode.syosetu.com/n8473hv
            val metadata = provider.getMetadata("n8473hv")
            metadata.introduction.shouldEndWith("たくさんの大切なもので満たされていくお話です。")
        }
        it("常规，R18重定向") {
            // https://ncode.syosetu.com/n5305eg
            val metadata = provider.getMetadata("n5305eg")
            metadata.title.shouldBe("【web版】エロいスキルで異世界無双")
        }
        it("短篇") {
            // https://ncode.syosetu.com/n0916hw
            val metadata = provider.getMetadata("n0916hw")
            metadata.introduction.shouldBeEmpty()
            metadata.toc.size.shouldBe(1)
        }
    }

    describe("getEpisode") {
        it("常规") {
            // https://ncode.syosetu.com/n9669bk/1
            val episode = provider.getEpisode("n9669bk", "1")
            episode.paragraphs.first().shouldBe("　俺は34歳住所不定無職。")
            episode.paragraphs.last().shouldBe("　俺はトラックとコンクリートに挟まれて、トマトみたいに潰れて死んだ。")
        }
        it("常规，R18重定向") {
            // https://ncode.syosetu.com/n5305eg/1
            val episode = provider.getEpisode("n5305eg", "1")
            episode.paragraphs.first().shouldBe("　「なんか、面白いことねーかなー」")
            episode.paragraphs.last().shouldBe("　プレイ確定！")
        }
        it("短篇") {
            // https://ncode.syosetu.com/n0916hw
            val episode = provider.getEpisode("n0916hw", "default")
            episode.paragraphs.first().shouldBe("\n")
            episode.paragraphs.last().shouldBe("\n")
        }
    }
})
