package infra.provider.providers

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith

class NovelismTest : DescribeSpec({
    val provider = Novelism()

    describe("getMetadata") {
        it("常规") {
            // https://novelism.jp/novel/2m0xulekSsCxfixwam8d7g
            val metadata = provider.getMetadata("2m0xulekSsCxfixwam8d7g")
            metadata.title.shouldBe("第四大戦")
            metadata.authors.first().name.shouldBe("十文字青")
            metadata.authors.first().link.shouldBe("https://novelism.jp/user/100010/")
            metadata.introduction.shouldStartWith("　人類は有史以前から人外勢力")
            metadata.introduction.shouldEndWith("戦いと日常の記録だ。")
            metadata.toc[0].title.shouldBe("世羽黙示録　Apocalypse of Yohane")
            metadata.toc[0].chapterId.shouldBeNull()
            metadata.toc[1].title.shouldBe("第１章　ゆっくり、丁寧に、歌うように")
            metadata.toc[1].chapterId.shouldBe("FPx-5OgYTGKkU_9HV3xahQ")
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
