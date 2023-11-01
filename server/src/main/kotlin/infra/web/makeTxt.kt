package infra.web

import infra.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createFile
import kotlin.io.path.notExists

suspend fun makeTxtFile(
    filePath: Path,
    lang: NovelFileLang,
    metadata: WebNovelMetadata,
    chapters: Map<String, WebNovelChapter>,
) {
    withContext(Dispatchers.IO) {
        if (filePath.notExists()) {
            filePath.createFile()
        }
        filePath.bufferedWriter().use {
            with(TxtWriter(lang)) {
                it.writeNovel(metadata, chapters)
            }
        }
    }
}

private class TxtWriter(
    private val lang: NovelFileLang,
) {
    private val jp = lang != NovelFileLang.ZH_BAIDU && lang != NovelFileLang.ZH_YOUDAO
    private val zh = lang != NovelFileLang.JP

    private fun BufferedWriter.writeTitle(novel: WebNovelMetadata) {
        if (jp) write(novel.titleJp + "\n")
        if (zh) write(novel.titleZh + "\n")
    }

    private fun BufferedWriter.writeAuthor(novel: WebNovelMetadata) {
        novel.authors.forEach { author ->
            write(author.name)
            if (author.link != null) {
                write("[${author.link}]")
            }
            write("\n")
        }
    }

    private fun BufferedWriter.writeIntroduction(novel: WebNovelMetadata) {
        if (jp && novel.introductionJp.isNotBlank()) {
            write(novel.introductionJp)
            write("\n")
        }
        if (zh && !novel.introductionZh.isNullOrBlank()) {
            write(novel.introductionZh)
            write("\n")
        }
    }

    private fun BufferedWriter.writeTocItemTitle(item: WebNovelTocItem) {
        if (jp) write("# ${item.titleJp}\n")
        if (zh) write("# ${item.titleZh ?: item.titleJp}\n")
    }

    private fun BufferedWriter.writeMissingChapter() {
        write("该章节缺失。\n\n")
    }

    private fun BufferedWriter.writeFallbackTranslate() {
        write("选择的翻译不存在，使用备用翻译。\n\n")
    }

    private fun BufferedWriter.writeJpParagraphs(
        jpParagraphs: List<String>?,
    ) {
        if (jpParagraphs == null) {
            writeMissingChapter()
        } else {
            jpParagraphs.forEach { write(it + "\n") }
        }
    }

    private fun BufferedWriter.writeZhParagraphs(
        primaryParagraphs: List<String>?,
        fallbackParagraphs: List<String>?
    ) {
        val isFallback = primaryParagraphs == null
        val paragraphs = primaryParagraphs ?: fallbackParagraphs

        if (paragraphs == null) {
            writeMissingChapter()
        } else {
            if (isFallback) {
                writeFallbackTranslate()
            }
            paragraphs.forEach { write(it + "\n") }
        }
    }

    private fun BufferedWriter.writeMixParagraphs(
        jpParagraphs: List<String>,
        primaryParagraphs: List<String>?,
        fallbackParagraphs: List<String>?
    ) {
        val isFallback = primaryParagraphs == null
        val paragraphs = primaryParagraphs ?: fallbackParagraphs

        if (paragraphs == null) {
            writeMissingChapter()
        } else {
            if (isFallback) {
                writeFallbackTranslate()
            }
            jpParagraphs.zip(paragraphs).forEach { (tZh, tJp) ->
                if (tZh.isNotBlank()) {
                    write(tJp + "\n")
                    write(tZh + "\n")
                } else {
                    write(tZh + "\n")
                }
            }
        }
    }

    private fun BufferedWriter.writeThreeParagraph(
        pJp: List<String>,
        pZh1: List<String>?,
        pZh2: List<String>?,
    ) {
        if (pZh1 == null || pZh2 == null) {
            writeMissingChapter()
        } else {
            for (i in pJp.indices) {
                val tJp = pJp[i]
                if (tJp.isNotBlank()) {
                    write(tJp + "\n")
                    write(pZh1[i] + "\n")
                    write(pZh2[i] + "\n")
                } else {
                    write(tJp + "\n")
                }
            }
        }
    }

    private fun BufferedWriter.writeChapter(
        chapter: WebNovelChapter,
    ) {
        val fallbackParagraphs =
            chapter.sakuraParagraphs
                ?: chapter.gptParagraphs
                ?: chapter.youdaoParagraphs
                ?: chapter.baiduParagraphs
        when (lang) {
            NovelFileLang.JP -> writeJpParagraphs(chapter.paragraphs)

            NovelFileLang.ZH_BAIDU -> writeZhParagraphs(chapter.baiduParagraphs, fallbackParagraphs)
            NovelFileLang.ZH_YOUDAO -> writeZhParagraphs(chapter.youdaoParagraphs, fallbackParagraphs)
            NovelFileLang.ZH_GPT -> writeZhParagraphs(chapter.gptParagraphs, fallbackParagraphs)
            NovelFileLang.ZH_SAKURA -> writeZhParagraphs(chapter.sakuraParagraphs, fallbackParagraphs)

            NovelFileLang.MIX_BAIDU -> writeMixParagraphs(
                chapter.paragraphs,
                chapter.baiduParagraphs,
                fallbackParagraphs,
            )

            NovelFileLang.MIX_YOUDAO -> writeMixParagraphs(
                chapter.paragraphs,
                chapter.youdaoParagraphs,
                fallbackParagraphs,
            )

            NovelFileLang.MIX_GPT -> writeMixParagraphs(
                chapter.paragraphs,
                chapter.gptParagraphs,
                fallbackParagraphs,
            )

            NovelFileLang.MIX_SAKURA -> writeMixParagraphs(
                chapter.paragraphs,
                chapter.sakuraParagraphs,
                fallbackParagraphs,
            )

            NovelFileLang.MIX_ALL -> writeThreeParagraph(
                chapter.paragraphs,
                chapter.youdaoParagraphs,
                chapter.baiduParagraphs,
            )
        }
    }

    fun BufferedWriter.writeNovel(
        novel: WebNovelMetadata,
        chapters: Map<String, WebNovelChapter>,
    ) {
        writeTitle(novel)
        write("\n")
        writeAuthor(novel)
        write("\n")
        write("#".repeat(12) + "\n")
        writeIntroduction(novel)
        write("\n\n\n")

        novel.toc.forEach { item ->
            writeTocItemTitle(item)
            write("\n")

            if (item.chapterId != null) {
                val chapter = chapters[item.chapterId]
                if (chapter == null) {
                    writeMissingChapter()
                } else {
                    writeChapter(chapter)
                }
                write("\n\n\n")
            }
        }
    }
}