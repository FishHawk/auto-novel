package infra.provider.providers

import infra.client
import domain.entity.WebNovelAttention
import domain.entity.WebNovelType
import infra.web.providers.Hameln
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.Instant

class HamelnTest : DescribeSpec({
    val provider = Hameln(client)

    describe("getMetadata") {
        it("常规") {
            // https://syosetu.org/novel/232822
            val metadata = provider.getMetadata("232822")
            metadata.title.shouldBe("和風ファンタジーな鬱エロゲーの名無し戦闘員に転生したんだが周囲の女がヤベー奴ばかりで嫌な予感しかしない件")
            metadata.introduction.shouldStartWith("どうやら和風ファンタジーゲ")
            metadata.type.shouldBe(WebNovelType.连载中)
            metadata.attentions.shouldContain(WebNovelAttention.R15)
            metadata.keywords.shouldContain("妖")
            metadata.toc[0].title.shouldBe("人物紹介・短編等")
            metadata.toc[0].chapterId.shouldBeNull()
            metadata.toc[0].createAt.shouldBeNull()
            metadata.toc[1].title.shouldBe("人物紹介(仮設)")
            metadata.toc[1].chapterId.shouldBe("1")
            metadata.toc[1].createAt.shouldBe(Instant.parse("2021-06-19T22:00:00Z"))
        }
        it("常规，作者无链接") {
            // https://syosetu.org/novel/305149
            val author = provider.getMetadata("305149").authors.first()
            author.name.shouldBe("文章修行僧")
            author.link.shouldBeNull()
        }
        it("常规，作者有链接") {
            // https://syosetu.org/novel/304380
            val author = provider.getMetadata("304380").authors.first()
            author.name.shouldBe("かりん2022")
            author.link.shouldBe("https://syosetu.org/user/347335/")
        }
        it("常规，R18重定向") {
            // https://syosetu.org/novel/94938
            val metadata = provider.getMetadata("94938")
            metadata.title.shouldBe("オズの国のドロシー")
        }
        it("常规，评价为空") {
            // https://syosetu.org/novel/274708
            val metadata = provider.getMetadata("274708")
            metadata.points.shouldBeNull()
        }
        it("短篇") {
            // https://syosetu.org/novel/303189
            val metadata = provider.getMetadata("303189")
            metadata.type.shouldBe(WebNovelType.短篇)
        }
    }

    describe("getChapter") {
        it("常规") {
            // https://syosetu.org/novel/321515/1.html
            val chapter = provider.getChapter("321515", "1")
            chapter.paragraphs[6].shouldBe("　『お父様』と呼ばれた男性は、十代後半から二十代前半程度の若い青年。『かぐや』と呼ばれた女性は、五歳程度の幼子であった。")
        }
        it("短篇") {
            // https://syosetu.org/novel/303596
            val chapter = provider.getChapter("303596", "default")
            chapter.paragraphs.size.shouldBe(141)
            chapter.paragraphs.first().shouldStartWith("　特級呪霊花御による、呪術高専東京校への襲撃")
        }
    }
})
