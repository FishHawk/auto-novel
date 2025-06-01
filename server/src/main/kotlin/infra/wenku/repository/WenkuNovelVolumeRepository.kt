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
import util.Signature as Sig
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
                        bf.appendLine("※ ${Sig.text()}")
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

            val contentOpfDoc = Epub.readContentOpf(jpPath)
            // 通过 content.opf 查找第一个文件
            val firstFile = contentOpfDoc
                ?.selectFirst("manifest")
                ?.selectFirst("item[href\$=html], item[href\$=xhtml]")
                ?.attr("href")
                ?: ""
            val navFile = contentOpfDoc
                ?.selectFirst("item[properties=nav]")
                ?.attr("href")
                ?: "nav file not found"
            // TODO(kuriko): 对于 EPUB v3 标准，nav 是必须的 (v2 使用 nav.ncx)
            //  因此这里可以添加一个检测，是否存在 nav 缺失。
            //  不过目前似乎没有遇到过有 epub 存在这种问题。

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
                } else if (name.contains(navFile)) {
                    bytesOut = Epub.addSigToNav(bytesOut)
                    bytesOut
                } else if (name.endsWith("opf")) {
                    bytesOut = Epub.fixInvalidOpf(bytesOut)
                    bytesOut
                } else if (name.endsWith("ncx")) { // toc.ncx file
                    Epub.addSigToNcx(bytesOut, firstFile)
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
