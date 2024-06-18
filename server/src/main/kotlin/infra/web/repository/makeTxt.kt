package infra.web.repository

import infra.common.TranslatorId
import infra.web.WebNovelMetadata
import infra.web.WebNovelTocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createFile
import kotlin.io.path.notExists

suspend fun makeTxtFile(
    filePath: Path,
    metadata: WebNovelMetadata,
    chapters: Map<String, ChapterWriteData>,
    jp: Boolean,
    zh: Boolean,
) {
    withContext(Dispatchers.IO) {
        if (filePath.notExists()) {
            filePath.createFile()
        }
        filePath.bufferedWriter().use {
            with(TxtWriter(jp, zh)) {
                it.writeNovel(metadata, chapters)
            }
        }
    }
}

private class TxtWriter(
    private val jp: Boolean,
    private val zh: Boolean,
) {
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

    private fun BufferedWriter.writeMissingTranslation(translation: TranslatorId) {
        write("${translation}翻译缺失。\n\n")
    }

    fun BufferedWriter.writeNovel(
        novel: WebNovelMetadata,
        chapters: Map<String, ChapterWriteData>,
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
                    chapter.missingTranslations.forEach {
                        writeMissingTranslation(it)
                    }

                    for (i in chapter.jpParagraphs.indices) {
                        if (chapter.jpParagraphs[i].isBlank()) {
                            write(chapter.jpParagraphs[i] + "\n")
                        } else {
                            chapter.paragraphs.forEach {
                                write(it.paragraphs[i] + "\n")
                            }
                        }
                    }
                }
                write("\n\n\n")
            }
        }
    }
}