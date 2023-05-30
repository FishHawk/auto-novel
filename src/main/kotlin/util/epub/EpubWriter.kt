package util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Attributes
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag
import java.io.BufferedOutputStream
import java.io.Closeable
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

class EpubWriter(
    path: Path,
    private val opfPath: String,
) : Closeable {
    private val stream = ZipOutputStream(BufferedOutputStream(path.outputStream()))
    override fun close() = stream.close()

    init {
        writeTextFile("mimetype", "application/epub+zip")
        writeTextFile("META-INF/container.xml", TEMPLATE_CONTAINER.format(opfPath))
    }

    private val opfDir
        get() = opfPath.substringBeforeLast("/", "")

    fun writeTextFile(path: String, content: String) {
        stream.putNextEntry(ZipEntry(path))
        stream.write(content.toByteArray())
    }

    fun writeBinaryFile(path: String, content: ByteArray) {
        stream.putNextEntry(ZipEntry(path))
        stream.write(content)
    }

    fun writeOpfFile(content: String) {
        writeTextFile(opfPath, content)
    }

    fun writeBook(book: EpubBook) {
        writeOpfFile(createPackageDocument(book))
        book.resources.forEach { doc ->
            val path = (if (opfDir.isNotBlank()) "$opfDir/" else opfDir) + doc.path
            writeTextFile(path, doc.content)
        }
    }

    companion object {
        private const val TEMPLATE_CONTAINER = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="%s" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>"""

        private const val TEMPLATE_PACKAGE = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0"/>"""

        private fun createPackageDocument(book: EpubBook): String {
            val doc = Jsoup.parse(TEMPLATE_PACKAGE, Parser.xmlParser())
            val rootElement = doc.selectFirst("package")!!
            rootElement.attr("unique-identifier", EpubBook.UNIQUE_IDENTIFIER)

            val metadataElement = createPackageSection(
                book.metadataSection,
                Element(
                    Tag.valueOf("metadata"), null,
                    Attributes().add("xmlns:dc", "http://purl.org/dc/elements/1.1/"),
                ),
            )
            val manifestElement = createPackageSection(
                book.manifestSection,
                Element(Tag.valueOf("manifest"), null)
            )
            val spineElement = createPackageSection(
                book.spineSection,
                Element(
                    Tag.valueOf("spine"), null,
                    Attributes().add("toc", "toc.ncx")
                )
            )
            rootElement.appendChild(metadataElement)
            rootElement.appendChild(manifestElement)
            rootElement.appendChild(spineElement)

            doc.outputSettings().prettyPrint(true)
            return doc.html()
        }

        private fun createPackageSection(section: List<EpubBook.PackageElement>, root: Element): Element {
            return section.fold(root) { parent, element ->
                val attributes = element.attributes.toList()
                    .fold(Attributes()) { attributes, (key, value) -> attributes.add(key, value) }
                val child = Element(Tag.valueOf(element.tag), null, attributes)
                if (element.value != null) child.appendText(element.value)
                parent.appendChild(child)
            }
        }
    }
}
