package infra.provider.providers

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.Instant

class KakuyomuTest : DescribeSpec({
    val provider = Kakuyomu()

    describe("getMetadata") {
        it("常规") {
            // https://kakuyomu.jp/works/1177354054892870623
            val metadata = provider.getMetadata("1177354054892870623")
            metadata.toc.forEach { println(it) }
            metadata.title.shouldBe("転生七女ではじめる異世界ライフ 〜万能魔力があれば貴族社会も余裕で生きられると聞いたのですが？！〜")
            metadata.authors.first().name.shouldBe("四葉夕卜")
            metadata.authors.first().link.shouldBe("https://kakuyomu.jp/users/yutoyotsuba")
            metadata.toc[0].title.shouldBe("第１章　アトウッド家")
            metadata.toc[0].chapterId.shouldBeNull()
            metadata.toc[0].createAt.shouldBeNull()
            metadata.toc[1].title.shouldBe("プロローグ")
            metadata.toc[1].chapterId.shouldBe("1177354054892870701")
            metadata.toc[1].createAt.shouldBe(Instant.parse("2019-12-10T03:00:12Z"))
        }
        it("常规，简介折叠") {
            // https://kakuyomu.jp/works/1177354054892870623
            val metadata = provider.getMetadata("1177354054892870623")
            metadata.introduction.shouldStartWith("　第５回カクヨムWEB小説コ")
            metadata.introduction.shouldEndWith("平穏な日々を目指すのだった。")
        }
        it("常规，简介无折叠") {
            // https://kakuyomu.jp/works/16817139555217983105
            val metadata = provider.getMetadata("16817139555217983105")
            metadata.introduction.shouldStartWith("俺はふとした時")
            metadata.introduction.shouldEndWith("てこうなった。")
        }
    }

    describe("getEpisode") {
        it("常规") {
            // https://kakuyomu.jp/works/16817139555217983105/episodes/16817139555218610896
            val episode = provider.getChapter("16817139555217983105", "16817139555218610896")
            episode.paragraphs.first().shouldBe("「あ～あ、女の子と甘酸っぱい青春を送りたいなぁ！」")
            episode.paragraphs.last().shouldBe("　俺はしばらく、その痕を見つめて本来の目的を忘れてしまうのだった。")
        }
    }
})
