package infra.web

import domain.entity.*
import infra.DataSourceMongo
import kotlinx.datetime.toKotlinInstant
import util.serialName
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readAttributes

class WebNovelFileRepository(
    private val mongo: DataSourceMongo,
) {
    private val root = Path("./data/files-web")

    suspend fun makeFile(
        providerId: String,
        novelId: String,
        mode: NovelFileMode,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
        type: NovelFileType,
    ): String? {
        val novel = mongo
            .webNovelMetadataCollection
            .findOne(WebNovelMetadata.byId(providerId, novelId))
            ?: return null

        val zhFilename = buildString {
            append("${providerId}.${novelId}.")
            append(mode.serialName())
            append('.')
            append(
                when (translationsMode) {
                    NovelFileTranslationsMode.Parallel -> "B"
                    NovelFileTranslationsMode.Priority -> "Y"
                }
            )
            translations.forEach {
                append(it.serialName()[0])
            }
            append('.')
            append(type.serialName())
        }

        val zhPath = root / zhFilename

        val shouldMake = if (zhPath.exists()) {
            val createAt = zhPath.readAttributes<BasicFileAttributes>()
                .creationTime()
                .toInstant()
                .toKotlinInstant()
            novel.changeAt > createAt
        } else true

        if (!shouldMake) return zhFilename

        val chapters = novel.toc
            .mapNotNull { it.chapterId }
            .mapNotNull { chapterId ->
                mongo.webNovelChapterCollection
                    .findOne(WebNovelChapter.byId(providerId, novelId, chapterId))
                    ?.let {
                        generateWriteInfoFromChapter(
                            chapter = it,
                            mode = mode,
                            translationsMode = translationsMode,
                            translations = translations,
                        )
                    }
                    ?.let { chapterId to it }
            }
            .toMap()

        when (type) {
            NovelFileType.EPUB -> makeEpubFile(
                zhPath,
                novel,
                chapters,
                jp = mode != NovelFileMode.Zh,
                zh = mode != NovelFileMode.Jp,
            )

            NovelFileType.TXT -> makeTxtFile(
                zhPath,
                novel,
                chapters,
                jp = mode != NovelFileMode.Zh,
                zh = mode != NovelFileMode.Jp,
            )
        }
        return zhFilename
    }
}

data class ParagraphsWriteData(
    val paragraphs: List<String>,
    val primary: Boolean,
)

data class ChapterWriteData(
    val missingTranslations: List<TranslatorId>,
    val jpParagraphs: List<String>,
    val paragraphs: List<ParagraphsWriteData>,
)

private fun generateWriteInfoFromChapter(
    chapter: WebNovelChapter,
    mode: NovelFileMode,
    translationsMode: NovelFileTranslationsMode,
    translations: List<TranslatorId>,
): ChapterWriteData {
    val missingTranslations = mutableListOf<TranslatorId>()

    fun getTranslation(id: TranslatorId) =
        when (id) {
            TranslatorId.Baidu -> chapter.baiduParagraphs
            TranslatorId.Youdao -> chapter.youdaoParagraphs
            TranslatorId.Gpt -> chapter.gptParagraphs
            TranslatorId.Sakura -> chapter.sakuraParagraphs
        }.also {
            if (it == null) missingTranslations.add(id)
        }

    val jpParagraphs = chapter.paragraphs
    val zhParagraphsList = when (translationsMode) {
        NovelFileTranslationsMode.Parallel ->
            translations.mapNotNull { getTranslation(it) }

        NovelFileTranslationsMode.Priority ->
            translations.firstNotNullOfOrNull { getTranslation(it) }
                ?.let { listOf(it) }
                ?: emptyList()
    }

    val paragraphs = when (mode) {
        NovelFileMode.Jp -> listOf(jpParagraphs).map { ParagraphsWriteData(it, true) }
        NovelFileMode.Zh -> zhParagraphsList.map { ParagraphsWriteData(it, true) }
        NovelFileMode.JpZh ->
            listOf(jpParagraphs).map { ParagraphsWriteData(it, false) } +
                    zhParagraphsList.map { ParagraphsWriteData(it, true) }

        NovelFileMode.ZhJp ->
            zhParagraphsList.map { ParagraphsWriteData(it, true) } +
                    listOf(jpParagraphs).map { ParagraphsWriteData(it, false) }
    }
    return ChapterWriteData(
        missingTranslations = missingTranslations,
        jpParagraphs = jpParagraphs,
        paragraphs = paragraphs,
    )
}
