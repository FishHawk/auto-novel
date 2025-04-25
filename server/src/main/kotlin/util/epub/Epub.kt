package util.epub

import infra.common.NovelFileMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import util.MachineTranslationSignature
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
            .attr("content", MachineTranslationSignature())

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
        return doc;
    }

    fun fixInvalidEpub(docString: String): String {
        var xmlString = docString;

        // NOTE(kuriko): fix iOS epub reader not working, Issue #85.
        // fix: replacing `<?xml version='1.0' encoding='utf-8'?>`
        val xmlDeclaration = """<?xml version="1.0" encoding="utf-8"?>"""
        val xmlDeclarationRegex = Regex("""<\?xml\s+.+?\?>""")
        xmlString = if (xmlDeclarationRegex.containsMatchIn(xmlString)) {
            // 找到，替换为标准 xml 规范
            xmlString.replace(xmlDeclarationRegex, xmlDeclaration)
        } else {
            // 未找到，创建一个新的
            "${xmlDeclaration}\n${xmlString}"
        }

        // fix: missing <!DOCTYPE html>
        // 实际上这个是 EPUB v3 规范，为了最大兼容性， 我们采用 EPUB v2 规范的 doctype
        // 毕竟说不好 content.opf 里面会不会写的是 <package version=2.0>
        val doctypeDeclaration = """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">"""
        val doctypeDeclarationRegex = Regex("""(?i)<!DOCTYPE\s+.*?>""")
        xmlString = if (doctypeDeclarationRegex.containsMatchIn(xmlString)) {
            // 找到，替换为标准 EPUB v2 规范
            xmlString.replace(doctypeDeclarationRegex, doctypeDeclaration)
        } else {
            // 未找到，创建一个新的
            xmlString.replace(xmlDeclarationRegex, "${xmlDeclaration}\n${doctypeDeclaration}")
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
}
