package infra.provider.providers

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.Instant

class NovelismTest : DescribeSpec({
    val provider = Novelism(client)

    describe("getMetadata") {
        it("常规") {
            // https://novelism.jp/novel/vDL5vTz_TTqrsEALu5zaOw
            val metadata = provider.getMetadata("vDL5vTz_TTqrsEALu5zaOw")
            metadata.title.shouldBe("鮮血王女、皆殺す")
            metadata.authors.first().name.shouldBe("kiki")
            metadata.authors.first().link.shouldBe("https://novelism.jp/user/100008/")
            metadata.introduction.shouldStartWith("メアリーは、")
            metadata.introduction.shouldEndWith("メアリーは姉を殺した全ての者への復讐を決意する。")
            metadata.toc[0].title.shouldBe("序章　産声が上がるのは君が死んだから")
            metadata.toc[0].chapterId.shouldBeNull()
            metadata.toc[0].createAt.shouldBeNull()
            metadata.toc[1].title.shouldBe("001　大切なもの、この両手からこぼれおちて")
            metadata.toc[1].chapterId.shouldBe("sKk9F9hLTJu3EA2k3hYLdg")
            metadata.toc[1].createAt.shouldBe(Instant.parse("2021-03-19T05:56:00Z"))
        }

        it("目录没有分段") {
            // https://novelism.jp/novel/TOOiVkXdS1ewhzdZSgiRmw/
            val metadata = provider.getMetadata("TOOiVkXdS1ewhzdZSgiRmw")
            metadata.toc[0].title.shouldBe("01話　プロローグ・シリアスな現実からシュールな異世界へ")
            metadata.toc[0].chapterId.shouldBe("ogpNPjmxTTyzqDI18BUndw")
        }
    }

    describe("getEpisode") {
        it("常规") {
            // https://novelism.jp/novel/2m0xulekSsCxfixwam8d7g/article/FPx-5OgYTGKkU_9HV3xahQ/
            val episode = provider.getChapter("2m0xulekSsCxfixwam8d7g", "FPx-5OgYTGKkU_9HV3xahQ")
            episode.paragraphs.first().shouldBe("Play like singing slowly and carefully")
        }

        it("注音/加重") {
            // https://novelism.jp/novel/pXGN1WNmQaaHKeoncr6vGQ/article/Gwl4xkGmQgaL01VTy196fQ/
            val episode = provider.getChapter("pXGN1WNmQaaHKeoncr6vGQ", "Gwl4xkGmQgaL01VTy196fQ")
            episode.paragraphs[14].shouldBe("　駅前のロータリーには、大手都銀の大看板も見える。地方銀行が幅を利かせる田舎に於いて、都市銀行の看板が並んでいるのは、それなりに栄えた町であることの証左だとも言えよう。")
            episode.paragraphs[24].shouldBe("「裸の女の子で一杯だってのに、ちっともありがたみがねぇな」")
        }

        it("图片") {
            // https://novelism.jp/novel/x8s_tHcoR3GlWDBqduYt3A/article/jED4QuNySX-3qBfjgdfRrA/
            val episode = provider.getChapter("x8s_tHcoR3GlWDBqduYt3A", "jED4QuNySX-3qBfjgdfRrA")
            episode.paragraphs[99].shouldBe("<图片>https://image.novelism.jp/media/illust/x8s_tHcoR3GlWDBqduYt3A/image_20210126092646.jpg")
        }
    }
})
