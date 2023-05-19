package epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.readText

class EpubReader(path: Path) {
    private val fs = FileSystems.newFileSystem(path)

    private fun getOpfPath(): String {
        val xmlContainer = getXhtmlFile("/META-INF/container.xml")
        return xmlContainer.selectFirst("rootfile")!!.attr("full-path")
    }

    fun getXhtmlFile(path: String): Document {
        return Jsoup.parse(fs.getPath(path).readText(), Parser.xmlParser())
    }

    fun listXhtmlFiles(): List<String> {
        val opfPath = getOpfPath()
        val opfDir = opfPath.substringBeforeLast("/", "")
        return getXhtmlFile(getOpfPath())
            .select("manifest item[media-type=application/xhtml+xml]")
            .map { opfDir + "/" + it.attr("href") }
    }
}