package epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.Closeable
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText

class EpubReader(path: Path) : Closeable {
    private val fs = FileSystems.newFileSystem(path)
    override fun close() = fs.close()

    fun getOpfPath(): String {
        val xmlContainer = readFileAsXHtml("/META-INF/container.xml")
        return xmlContainer.selectFirst("rootfile")!!.attr("full-path")
    }

    fun readFileAsText(path: String): String {
        return fs.getPath(path).readText()
    }

    fun readFileAsBinary(path: String): ByteArray {
        return fs.getPath(path).readBytes()
    }

    fun readFileAsXHtml(path: String): Document {
        return Jsoup.parse(fs.getPath(path).readText(), Parser.xmlParser())
    }

    fun listXhtmlFiles(): List<String> {
        val opfPath = getOpfPath()
        val opfDir = opfPath.substringBeforeLast("/", "")
        return readFileAsXHtml(getOpfPath())
            .select("manifest item[media-type=application/xhtml+xml]")
            .map { opfDir + "/" + it.attr("href") }
    }

    fun listFiles(): List<String> {
        val opfPath = getOpfPath()
        val opfDir = opfPath.substringBeforeLast("/", "")
        return readFileAsXHtml(getOpfPath())
            .select("manifest item")
            .map { opfDir + "/" + it.attr("href") }
    }
}