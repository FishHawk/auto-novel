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

    companion object {
        private const val MISSING_EPISODE_HINT = "该章节缺失。\n\n"
    }

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

    private fun BufferedWriter.writeMissingEpisode() {
        write(MISSING_EPISODE_HINT)
    }

    private fun BufferedWriter.writeOneParagraph(
        p: List<String>?,
    ) {
        if (p == null) {
            writeMissingEpisode()
        } else {
            p.forEach { write(it + "\n") }
        }
    }

    private fun BufferedWriter.writeTwoParagraph(
        pJp: List<String>,
        pZh: List<String>?,
    ) {
        if (pZh == null) {
            writeMissingEpisode()
        } else {
            pJp.zip(pZh).forEach { (tZh, tJp) ->
                if (tZh.isNotBlank()) {
                    write(tJp + "\n")
                    write(tZh + "\n")
                } else {
                    write(tZh + "\n")
                }
            }
        }
    }

    private fun BufferedWriter.writeChapter(
        chapter: WebNovelChapter,
    ) {
        when (lang) {
            NovelFileLang.JP -> writeOneParagraph(chapter.paragraphs)
            NovelFileLang.ZH_BAIDU -> writeOneParagraph(chapter.baiduParagraphs)
            NovelFileLang.ZH_YOUDAO -> writeOneParagraph(chapter.youdaoParagraphs)
            NovelFileLang.MIX_BAIDU -> writeTwoParagraph(chapter.paragraphs, chapter.baiduParagraphs)
            NovelFileLang.MIX_YOUDAO -> writeTwoParagraph(chapter.paragraphs, chapter.youdaoParagraphs)
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
                    writeMissingEpisode()
                } else {
                    writeChapter(chapter)
                }
                write("\n\n\n")
            }
        }
    }
}