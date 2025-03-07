package infra.provider.providers

import koinExtensions
import infra.web.datasource.WebNovelHttpDataSource
import infra.web.datasource.providers.Syosetu
import infra.web.WebNovelAttention
import infra.web.WebNovelType
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.Instant
import org.koin.test.KoinTest
import org.koin.test.inject

class SyosetuTest : DescribeSpec(), KoinTest {
    override fun extensions() = koinExtensions()
    private val dataSource by inject<WebNovelHttpDataSource>()
    private val provider get() = dataSource.providers[Syosetu.id]!!

    init {
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
                metadata.attentions.shouldContain(WebNovelAttention.R18)
                metadata.type.shouldBe(WebNovelType.已完结)
            }
            it("短篇") {
                // https://ncode.syosetu.com/n0916hw
                val metadata = provider.getMetadata("n0916hw")
                metadata.toc.size.shouldBe(1)
            }
        }

        describe("getChapter") {
            it("常规") {
                // https://ncode.syosetu.com/n9669bk/1
                val chapter = provider.getChapter("n9669bk", "1")
                chapter.paragraphs.first().shouldBe("　俺は34歳住所不定無職。")
                chapter.paragraphs.last().shouldBe("　俺はトラックとコンクリートに挟まれて、トマトみたいに潰れて死んだ。")
            }
            it("常规，R18重定向") {
                // https://ncode.syosetu.com/n5305eg/1
                val chapter = provider.getChapter("n5305eg", "1")
                chapter.paragraphs.first().shouldBe("　「なんか、面白いことねーかなー」")
                chapter.paragraphs.last().shouldBe("　プレイ確定！")
            }
            it("短篇") {
                // https://ncode.syosetu.com/n0916hw
                val chapter = provider.getChapter("n0916hw", "default")
                chapter.paragraphs.shouldHaveAtLeastSize(30)
                chapter.paragraphs.first().shouldBe("")
                chapter.paragraphs.last().shouldBe("詳細は活動報告もしくは本文下のリンクをご確認ください！")
            }
            it("ruby标签过滤") {
                // https://ncode.syosetu.com/n2907ga/74
                val chapter = provider.getChapter("n2907ga", "74")
                chapter.paragraphs[17].shouldEndWith("皮肉にも人の理の外にあるものだったのか」")
                chapter.paragraphs[29].shouldBe("「私が思うのと同程度に、彼女があなたにとって大事な存在になっただって？　―――あり得ない話だ」")
            }
            it("插图") {
                // https://ncode.syosetu.com/n9025bp/56
                val chapter = provider.getChapter("n9025bp", "56")
                chapter.paragraphs[1].shouldStartWith("<图片>")
            }
        }
    }
}
