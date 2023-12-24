package infra.web

import infra.DataSourceMongo
import infra.model.*
import kotlinx.datetime.toKotlinInstant
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
        lang: NovelFileLangV2,
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
            append(
                when (lang) {
                    NovelFileLangV2.Jp -> "jp"
                    NovelFileLangV2.Zh -> "zh"
                    NovelFileLangV2.JpZh -> "jp-zh"
                    NovelFileLangV2.ZhJp -> "zh-jp"
                }
            )
            append('.')
            append(
                when (translationsMode) {
                    NovelFileTranslationsMode.Parallel -> "B"
                    NovelFileTranslationsMode.Priority -> "Y"
                }
            )
            translations.forEach {
                append(
                    when (it) {
                        TranslatorId.Baidu -> "b"
                        TranslatorId.Youdao -> "y"
                        TranslatorId.Gpt -> "g"
                        TranslatorId.Sakura -> "s"
                    }
                )
            }
            append(
                when (type) {
                    NovelFileType.EPUB -> ".epub"
                    NovelFileType.TXT -> ".txt"
                }
            )
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
                            lang = lang,
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
                jp = lang != NovelFileLangV2.Zh,
                zh = lang != NovelFileLangV2.Jp,
            )

            NovelFileType.TXT -> makeTxtFile(
                zhPath,
                novel,
                chapters,
                jp = lang != NovelFileLangV2.Zh,
                zh = lang != NovelFileLangV2.Jp,
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
    lang: NovelFileLangV2,
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

    val paragraphs = when (lang) {
        NovelFileLangV2.Jp -> listOf(jpParagraphs).map { ParagraphsWriteData(it, true) }
        NovelFileLangV2.Zh -> zhParagraphsList.map { ParagraphsWriteData(it, true) }
        NovelFileLangV2.JpZh ->
            listOf(jpParagraphs).map { ParagraphsWriteData(it, false) } +
                    zhParagraphsList.map { ParagraphsWriteData(it, true) }

        NovelFileLangV2.ZhJp ->
            zhParagraphsList.map { ParagraphsWriteData(it, true) } +
                    listOf(jpParagraphs).map { ParagraphsWriteData(it, false) }
    }
    return ChapterWriteData(
        missingTranslations = missingTranslations,
        jpParagraphs = jpParagraphs,
        paragraphs = paragraphs,
    )
}
