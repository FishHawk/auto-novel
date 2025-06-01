package util.epub

import infra.common.NovelFileMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import util.Signature as Sig
import java.io.BufferedOutputStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.streams.asSequence


private fun FileSystem.readFileAsXHtml(path: String): Document {
    return Jsoup.parse(getPath(path).readText(), Parser.xmlParser())
}

object Epub {
    fun forEachXHtmlFile(path: Path, block: (xhtmlPath: String, doc: Document) -> Unit) {
        FileSystems.newFileSystem(path).use { fs ->
            val xmlContainer = fs.readFileAsXHtml("/META-INF/container.xml")
            val opfPath = xmlContainer.selectFirst("rootfile")!!.attr("full-path")

            val opfDir = opfPath.substringBeforeLast("/", "")
            fs.readFileAsXHtml(opfPath)
                .select("manifest item[media-type=application/xhtml+xml]")
                .map { opfDir + "/" + it.attr("href") }
                .forEach { block(it, fs.readFileAsXHtml(it)) }
        }
    }

    fun readContentOpf(srcPath: Path): Document? {
        return FileSystems.newFileSystem(srcPath).use { fs ->
            Files
                .walk(fs.rootDirectories.first())
                .filter { it.isRegularFile() }
                .asSequence()
                .forEach { path ->
                    val name = path.toString().removePrefix("/")
                    if (name.contains("content.opf")) {
                        val doc = Jsoup.parse(path.readBytes().decodeToString())
                        return@use doc
                    }
                }
            null
        }
    }

    inline fun modify(
        srcPath: Path,
        dstPath: Path,
        modify: (name: String, bytes: ByteArray) -> ByteArray,
    ) {
        FileSystems.newFileSystem(srcPath).use { fs ->
            ZipOutputStream(BufferedOutputStream(dstPath.outputStream())).use { zipOut ->
                Files
                    .walk(fs.rootDirectories.first())
                    .filter { it.isRegularFile() }
                    .sorted { path1, path2 ->
                        val pathToNumber = { path: Path ->
                            val s = path.toString()
                            when {
                                s == "/mimetype" -> 3
                                s == "/META-INF/container.xml" -> 2
                                s.endsWith("opf") -> 1
                                else -> 0
                            }
                        }
                        val n1 = pathToNumber(path1)
                        val n2 = pathToNumber(path2)
                        if (n1 != n2) {
                            n2.compareTo(n1)
                        } else {
                            path1.compareTo(path2)
                        }
                    }
                    .asSequence()
                    .forEach { path ->
                        val name = path.toString().removePrefix("/")
                        val bytesIn = path.readBytes()
                        val bytesOut = modify(name, bytesIn)
                        zipOut.putNextEntry(ZipEntry(name))
                        zipOut.write(bytesOut)
                        zipOut.closeEntry()
                    }
            }
        }
    }

    fun replaceWithTranslation(
        doc: Document,
        zhLinesList: List<List<String>>,
        mode: NovelFileMode,
    ): Document {
        // 添加机翻标识, Issue #134
        doc.head().appendElement("meta")
            .attr("name", "translation")
            .attr("content", Sig.text())

        // Fix: <html xmlns="..." xmlns:epub="..." xml:lang="ja" class="vrtl">
        doc.head()
            .attr("xml:lang", "zh-CN")
            .attr("lang", "zh-CN")
            .removeAttr("class")

        doc.select("p")
            .filter { el -> el.text().isNotBlank() }
            .forEachIndexed { index, el ->
                when (mode) {
                    NovelFileMode.Jp -> throw RuntimeException("文库小说不允许日语下载")
                    NovelFileMode.Zh -> {
                        zhLinesList.forEach { lines ->
                            el.before("<p>${lines[index]}</p>")
                        }
                        el.remove()
                    }

                    NovelFileMode.JpZh -> {
                        zhLinesList.asReversed().forEach { lines ->
                            el.after("<p>${lines[index]}</p>")
                        }
                        el.attr("style", "opacity:0.4;")
                    }

                    NovelFileMode.ZhJp -> {
                        zhLinesList.forEach { lines ->
                            el.before("<p>${lines[index]}</p>")
                        }
                        el.attr("style", "opacity:0.4;")
                    }
                }
            }
        doc.outputSettings().prettyPrint(true)
        return doc
    }

    // 防止每次调用 fixInvalidEpub 的时候都重新编译 regex 和创建 String Obj
    private val xmlDeclarationRegex = Regex("""<\?xml\s+.+?\?>""")
    private const val XML_DECLARATION = """<?xml version="1.0" encoding="utf-8"?>"""

    private val doctypeDeclarationRegex = Regex("""(?i)<!DOCTYPE\s+.*?>""")
    private const val DOCTYPE_DECLARATION = """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">"""
    fun fixInvalidEpub(docString: String): String {
        var xmlString = docString

        // NOTE(kuriko): fix iOS epub reader not working, Issue #85.
        // fix: replacing `<?xml version='1.0' encoding='utf-8'?>`
        xmlString = if (xmlDeclarationRegex.containsMatchIn(xmlString)) {
            xmlString.replace(xmlDeclarationRegex, XML_DECLARATION)
        } else {
            "${XML_DECLARATION}\n${xmlString}"
        }

        // fix: missing <!DOCTYPE html>
        // 实际上这个是 EPUB v3 规范，为了最大兼容性， 我们采用 EPUB v2 规范的 doctype
        // 毕竟说不好 content.opf 里面会不会写的是 <package version=2.0>
        xmlString = if (doctypeDeclarationRegex.containsMatchIn(xmlString)) {
            xmlString.replace(doctypeDeclarationRegex, DOCTYPE_DECLARATION)
        } else {
            xmlString.replace(xmlDeclarationRegex, "${XML_DECLARATION}\n${DOCTYPE_DECLARATION}")
        }

        val doc = Jsoup.parse(xmlString, Parser.xmlParser())

        // fix: missing
        //     <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
        doc
            .selectFirst("head")
            ?.apply {
                val metaNode = Element("meta")
                    .attr("http-equiv", "Content-Type")
                    .attr("content", "application/xml; charset=UTF-8")
                selectFirst("meta[http-equiv=Content-Type]")
                ?.replaceWith(metaNode)
                ?: appendChild(metaNode)
            }

        doc.outputSettings().prettyPrint(true)
        return doc.html()
    }

    fun fixInvalidOpf(bytesIn: ByteArray): ByteArray {
        val doc = Jsoup.parse(bytesIn.decodeToString(), Parser.xmlParser())

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
            ?.appendChild(Element("dc:description").text(Sig.text()))

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
        return doc.html().toByteArray()
    }

    fun addSigToNcx(bytesIn: ByteArray, link: String?): ByteArray {
        val doc = Jsoup.parse(bytesIn.decodeToString())

        doc.selectFirst("navMap")
            ?.insertChildren(0, Sig.epubNcx(link))

        doc.outputSettings().prettyPrint(true)
        return doc.html().toByteArray()
    }

    fun addSigToNav(bytesIn: ByteArray): ByteArray {
        val doc = Jsoup.parse(bytesIn.decodeToString())

        // FIXME(kuriko): 目前还没看到有用 epub v3 标准的生肉，
        //  因此这个大概、也许不会被触发。
        //  另外尴尬的是，如果真的用 v3，生肉的排版很可能也没有用 ol-li 形式
        //  说不定大概率用的是 p 暴力列举。
        //  因此直接尝试暴力将 sig 加载 body 开头。
        doc.selectFirst("body")
            ?.insertChildren(0, Sig.epubNav)

        doc.outputSettings().prettyPrint(true)
        return doc.html().toByteArray()
    }
}
