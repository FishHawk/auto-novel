package infra.wenku.repository

import infra.TempFileClient
import infra.TempFileType
import infra.common.NovelFileMode
import infra.common.NovelFileTranslationsMode
import infra.common.TranslatorId
import infra.wenku.WenkuNovelVolumeList
import infra.wenku.datasource.WenkuNovelVolumeDiskDataSource
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.jsoup.nodes.Element
import util.MachineTranslationSignature
import util.epub.Epub
import util.serialName
import java.security.MessageDigest
import kotlin.io.path.*

private fun String.escapePath() =
    replace('/', '.')

class WenkuNovelVolumeRepository(
    private val fs: WenkuNovelVolumeDiskDataSource,
    private val temp: TempFileClient,
) {
    private fun volumesDir(novelId: String) =
        Path("./data/files-wenku/${novelId}")

    suspend fun list(novelId: String): WenkuNovelVolumeList =
        fs.listVolumes(volumesDir(novelId))

    suspend fun createVolume(
        novelId: String,
        volumeId: String,
        inputStream: ByteReadChannel,
        unpack: Boolean,
    ) = fs.createVolume(
        volumesDir = volumesDir(novelId),
        volumeId = volumeId,
        inputStream = inputStream,
        unpack = unpack,
    )

    suspend fun deleteVolume(
        novelId: String,
        volumeId: String,
    ) = fs.deleteVolume(
        volumesDir = volumesDir(novelId),
        volumeId = volumeId,
    )

    suspend fun getVolume(
        novelId: String,
        volumeId: String,
    ) = fs.getVolume(
        volumesDir = volumesDir(novelId),
        volumeId = volumeId,
    )

    suspend fun makeTranslationVolumeFile(
        novelId: String,
        volumeId: String,
        mode: NovelFileMode,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
    ) = withContext(Dispatchers.IO) {
        val volume = getVolume(novelId, volumeId)
            ?: return@withContext null

        fun md5(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            return digest.fold("") { str, it -> str + "%02x".format(it) }
        }

        val zhFilename = buildString {
            append(novelId)
            append('.')
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
            append(md5(volumeId))
            append('.')
            append(volumeId.substringAfterLast('.', "txt"))
        }

        val zhPath = temp.createFile(TempFileType.Wenku, zhFilename)

        suspend fun getZhLinesList(chapterId: String): List<List<String>> {
            return when (translationsMode) {
                NovelFileTranslationsMode.Parallel ->
                    translations.mapNotNull { volume.getTranslation(it, chapterId) }

                NovelFileTranslationsMode.Priority ->
                    translations.firstNotNullOfOrNull { volume.getTranslation(it, chapterId) }
                        ?.let { listOf(it) }
                        ?: emptyList()
            }
        }

        if (volumeId.endsWith(".txt")) {
            zhPath.bufferedWriter().use { bf ->
                volume.listChapter().sorted().forEach { chapterId ->
                    val zhLinesList = getZhLinesList(chapterId)
                    if (zhLinesList.isEmpty()) {
                        bf.appendLine("// 该分段翻译缺失。")
                    } else {
                        // 添加机翻标识, Issue #134
                        bf.appendLine("※ ${MachineTranslationSignature()}")
                        bf.appendLine("")

                        val jpLines = volume.getChapter(chapterId)!!
                        val linesList = when (mode) {
                            NovelFileMode.Jp -> throw RuntimeException("文库小说不允许日语下载")
                            NovelFileMode.Zh -> zhLinesList
                            NovelFileMode.JpZh -> listOf(jpLines) + zhLinesList
                            NovelFileMode.ZhJp -> zhLinesList + listOf(jpLines)
                        }
                        for (i in jpLines.indices) {
                            linesList.forEach { lines ->
                                bf.appendLine(lines[i])
                            }
                        }
                    }
                }
            }
            return@withContext zhFilename
        } else {
            val jpPath = volume.volumesDir / volumeId

            val chapters = volume.listChapter()
            Epub.modify(srcPath = jpPath, dstPath = zhPath) { name, bytesIn ->
                var bytesOut = bytesIn

                // 为了兼容ChapterId以斜杠开头的旧格式
                val chapterId = if ("/${name}".escapePath() in chapters) {
                    "/${name}".escapePath()
                } else if (name.escapePath() in chapters) {
                    name.escapePath()
                } else {
                    null
                }

                // FIXME(kuriko): 这个可能会给服务器带来更大的负载（每次都需要重新跑正则），
                //  可能需要在上传的时候进行这个 fix，之后将结果 cache 起来
                if (name.endsWith("html") || name.endsWith("xhtml")) {
                    val fixedDoc = Epub.fixInvalidEpub(bytesOut.decodeToString())
                    bytesOut =  fixedDoc.toByteArray()
                }

                if (chapterId != null) {
                    // XHtml文件，尝试生成翻译版
                    val zhLinesList = getZhLinesList(chapterId)
                    if (zhLinesList.isEmpty()) {
                        bytesOut
                    } else {
                        val doc = Jsoup.parse(bytesOut.decodeToString(), Parser.xmlParser())
                        Epub.replaceWithTranslation(doc, zhLinesList, mode);
                        doc.outputSettings().prettyPrint(true)
                        doc.html().toByteArray()
                    }
                } else if (name.endsWith("opf")) {
                    val doc = Jsoup.parse(bytesOut.decodeToString(), Parser.xmlParser())

                    // 防止部分阅读器使用竖排
                    doc
                        .selectFirst("spine")
                        ?.removeAttr("page-progression-direction")

                    // 修改 EPUB 语言为简体中文（让 iOS iBook 阅读器可以使用中文字体）
                    // Fix Issue #58
                    doc
                        .selectFirst("metadata")
                        ?.apply {
                            selectFirst("dc|language")
                            ?.text("zh-CN")
                            ?: appendChild(Element("dc:language").text("zh-CN"))
                        }

                    // 添加机翻标识, Issue #134
                    doc
                        .selectFirst("metadata")
                        ?.appendChild(
                            Element("dc:description")
                                .text(MachineTranslationSignature()
                            )
                        )

                    // fix: 竖排转横排文本后，翻页方向问题。 Issue #107
                    //     <meta name="primary-writing-mode" content="vertical-rl"/>
                    //     <meta name="primary-writing-mode" content="horizontal-lr"/>
                    doc
                        .selectFirst("metadata")
                        ?.apply {
                            val metaNode = Element("meta")
                                .attr("name", "primary-writing-mode")
                                .attr("content", "horizontal-lr")
                            selectFirst("meta[name=primary-writing-mode]")
                                ?.replaceWith(metaNode)
                                ?: appendChild(metaNode)
                        }

                    doc.outputSettings().prettyPrint(true)
                    doc.html().toByteArray()
                } else if (name.endsWith("css")) {
                    "".toByteArray()
                } else {
                    bytesOut
                }
            }
            return@withContext zhFilename
        }
    }
}
