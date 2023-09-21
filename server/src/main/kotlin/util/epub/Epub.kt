package util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText

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
        modify: (entry: ZipEntry, bytes: ByteArray) -> ByteArray,
    ) {
        ZipInputStream(BufferedInputStream(srcPath.inputStream())).use { zipIn ->
            ZipOutputStream(BufferedOutputStream(dstPath.outputStream())).use { zipOut ->
                generateSequence { zipIn.nextEntry }
                    .filterNot { it.isDirectory }
                    .forEach {
                        val bytesIn = zipIn.readAllBytes()
                        val bytesOut = modify(it, bytesIn)
                        zipOut.putNextEntry(ZipEntry(it.name))
                        zipOut.write(bytesOut)
                        zipOut.closeEntry()
                    }
            }
        }
    }
}
