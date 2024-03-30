package infra.provider.providers

import infra.client
import infra.web.providers.NovelIdShouldBeReplacedException
import infra.web.providers.Pixiv
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class PixivTest : DescribeSpec({
    val provider = Pixiv(client)

    describe("getMetadata") {
        it("常规") {
            // https://www.pixiv.net/novel/series/9406879
            val metadata = provider.getMetadata("9406879")
            metadata.title.shouldBe("メス堕ちシリーズ")
            metadata.authors.first().name.shouldBe("から")
            metadata.authors.first().link.shouldBe("https://www.pixiv.net/users/46135214")
            metadata.introduction.shouldBe(
                "男子高校生が振った同級生の友達から復讐されるお話です。\n" +
                        "TSF、女装無理な方はブラウザバック推奨です。"
            )
            metadata.toc[0].title.shouldBe("女装→メス堕ち→TSF")
            metadata.toc[0].chapterId.shouldBe("18304868")
            metadata.toc[1].title.shouldBe("女装→メス堕ち→TSF")
            metadata.toc[1].chapterId.shouldBe("18457702")
        }
        it("常规，章节标签合并") {
            // https://www.pixiv.net/novel/series/898392
            val metadata = provider.getMetadata("898392")
            metadata.keywords.shouldNotBeEmpty()
        }
        it("常规，非R18") {
            // https://www.pixiv.net/novel/series/10539710
            val metadata = provider.getMetadata("10539710")
        }
        it("常规，好友限定") {
            // https://www.pixiv.net/novel/series/642636
            val metadata = provider.getMetadata("642636")
        }
        it("常规，目录很多页") {
            // https://www.pixiv.net/novel/series/870363
            val metadata = provider.getMetadata("870363")
            metadata.toc[0].title.shouldBe("【番外編】強運少女は夢をかなえた？")
            metadata.toc[0].chapterId.shouldBe("8592479")
        }
        it("短篇") {
            // https://www.pixiv.net/novel/show.php?id=19776346
            val metadata = provider.getMetadata("s19776346")
            metadata.title.shouldBe("メカクレ青年が全自動矯正機で熟女にとりさんにされる話")
            metadata.authors.first().name.shouldBe("たれ")
            metadata.authors.first().link.shouldBe("https://www.pixiv.net/users/60498514")
            metadata.toc[0].title.shouldBe("无名")
            metadata.toc[0].chapterId.shouldBe("19776346")
        }
        it("短篇，但存在系列") {
            // https://www.pixiv.net/novel/show.php?id=18304868
            shouldThrow<NovelIdShouldBeReplacedException> {
                provider.getMetadata("s18304868")
            }
        }
    }

    describe("getEpisode") {
        it("常规") {
            // https://www.pixiv.net/novel/show.php?id=18304868
            val chapter = provider.getChapter("9406879", "18304868")
            chapter.paragraphs.first().shouldBe("「私と付き合ってください。」")
            chapter.paragraphs.last().shouldBe("俺はそれに従うしかなかった。")
        }
        it("常规，多页") {
            // https://www.pixiv.net/novel/show.php?id=18199707
            val chapter = provider.getChapter("870363", "18199707")
            chapter.paragraphs.first().shouldBe("　｢ふわぁ～あ～……久々に凄い暇で眠いな～……｣")
            chapter.paragraphs.last().shouldBe("　風香の叫びが青空に響いた。")
        }
        it("插图模式1") {
            // https://www.pixiv.net/novel/show.php?id=10723739
            // [uploadedimage:42286]
            val chapter = provider.getChapter("s10723739", "10723739")
            val line = chapter.paragraphs.find { it.startsWith("<图片>") }
            line.shouldBe("<图片>https://i.pximg.net/novel-cover-original/img/2021/04/29/09/02/19/tei682410579700_b92972c74f71d56a7c436837c4f5b959.jpg")
        }
        it("插图模式2") {
            // https://www.pixiv.net/novel/show.php?id=2894162
            // [pixivimage:38959194]
            val chapter = provider.getChapter("222297", "2894162")
            val line = chapter.paragraphs.find { it.startsWith("<图片>") }
            line.shouldBe("<图片>https://i.pximg.net/img-original/img/2013/10/06/21/04/43/38959194_p0.jpg")
        }
        it("ruby") {
            // https://www.pixiv.net/novel/show.php?id=10618179
            // [[rb:久世彩葉 > くぜ いろは]]
            val chapter = provider.getChapter("s10618179", "10618179")
            chapter.paragraphs[2].shouldStartWith("　私、久世彩葉がその")
        }
    }
})
