package epub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Attributes
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag
import java.io.BufferedOutputStream
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

class EpubBook {
    companion object {
        const val UNIQUE_IDENTIFIER = "pub-id"
    }

    data class PackageElement(
        val tag: String,
        val value: String?,
        val attributes: Map<String, String>,
    ) {
        companion object {
            fun commonAttributes(dir: String?, id: String?, xmlLang: String?): Map<String, String?> {
                return mapOf("dir" to dir, "id" to id, "xml:lang" to xmlLang)
            }
        }
    }

    val metadataSection = mutableListOf<PackageElement>()
    val manifestSection = mutableListOf<PackageElement>()
    val spineSection = mutableListOf<PackageElement>()
    val resources = mutableListOf<EpubResource>()

    @Suppress("UNCHECKED_CAST")
    private fun addPackageElement(
        section: MutableList<PackageElement>,
        tag: String,
        value: String? = null,
        attributes: Map<String, String?> = emptyMap(),
    ) {
        section.add(PackageElement(
            tag, value,
            attributes.filter { it.value != null } as Map<String, String>
        ))
    }

    fun addIdentifier(value: String, isUnique: Boolean) {
        addPackageElement(
            metadataSection,
            "dc:identifier", value,
            if (isUnique) mapOf("id" to UNIQUE_IDENTIFIER) else emptyMap()
        )
    }

    fun addTitle(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:title", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addLanguage(value: String, id: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:language", value,
            mapOf("id" to id)
        )
    }

    fun addContributor(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:contributor", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addCoverage(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:coverage", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addCreator(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:creator", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addDate(value: LocalDateTime, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        val dateString = DateTimeFormatter.ISO_DATE_TIME.format(value)
        addPackageElement(
            metadataSection,
            "dc:date", dateString,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addDescription(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:description", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addFormat(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:format", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addPublisher(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:publisher", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addRelation(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:relation", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addRights(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "rights", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addSource(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:source", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addSubject(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:subject", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addType(value: String, dir: String? = null, id: String? = null, xmlLang: String? = null) {
        addPackageElement(
            metadataSection,
            "dc:type", value,
            PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addMeta(
        value: String,
        property: String,
        dir: String? = null,
        id: String? = null,
        refines: String? = null,
        scheme: String? = null,
        xmlLang: String? = null,
    ) {
        addPackageElement(
            metadataSection,
            "meta", value,
            mapOf("property" to property, "refines" to refines, "scheme" to scheme) +
                    PackageElement.commonAttributes(dir, id, xmlLang)
        )
    }

    fun addLink(
        href: String,
        rel: String,
        hrefLang: String? = null,
        id: String? = null,
        mediaType: String? = null,
        properties: String? = null,
        refines: String? = null,
    ) {
        addPackageElement(
            metadataSection,
            "link", null,
            mapOf(
                "href" to href,
                "href-lang" to hrefLang,
                "id" to id,
                "media-type" to mediaType,
                "properties" to properties,
                "refines" to refines,
                "rel" to rel,
            )
        )
    }

    fun addResourceToManifest(
        href: String,
        id: String,
        mediaType: String,
        fallback: String? = null,
        mediaOverlay: String? = null,
        properties: String? = null,
    ) {
        addPackageElement(
            manifestSection,
            "item", null,
            mapOf(
                "href" to href,
                "id" to id,
                "media-type" to mediaType,
                "fallback" to fallback,
                "media-overlay" to mediaOverlay,
                "properties" to properties,
            )
        )
    }

    fun addResourceRefToSpine(
        idref: String,
        id: String? = null,
        linear: Boolean = true,
        properties: String? = null,
    ) {
        addPackageElement(
            spineSection,
            "itemref", null,
            mapOf(
                "idref" to idref,
                "id" to id,
                "iinear" to if (linear) null else "no",
                "iroperties" to properties,
            )
        )
    }

    fun addResource(resource: EpubResource, linear: Boolean? = null) {
        resources.add(resource)
        addResourceToManifest(
            href = resource.path,
            id = resource.id,
            mediaType = resource.mediaType,
            properties = resource.properties
        )
        if (linear != null) {
            addResourceRefToSpine(idref = resource.id, linear = linear)
        }
    }

    fun addNavigation(
        identifier: String,
        navigation: Navigation,
    ) {
        addResource(createEpubNav(navigation), true)
        addResource(createEpubNcx(identifier, navigation))
    }

    suspend fun write(filePath: Path) = Writer(this).write(filePath)

    private class Writer(val book: EpubBook) {
        suspend fun write(filePath: Path) {
            withContext(Dispatchers.IO) {
                if (filePath.notExists()) {
                    filePath.createFile()
                }
                ZipOutputStream(BufferedOutputStream(filePath.outputStream())).use {
                    it.putNextEntry(ZipEntry("META-INF/container.xml"))
                    it.write(TEMPLATE_CONTAINER.toByteArray())

                    it.putNextEntry(ZipEntry("mimetype"))
                    it.write("application/epub+zip".toByteArray())

                    it.putNextEntry(ZipEntry("OEBPS/content.opf"))
                    it.write(createPackageDocument().toByteArray())

                    book.resources.forEach { doc ->
                        it.putNextEntry(ZipEntry("OEBPS/" + doc.path))
                        it.write(doc.content.toByteArray())
                    }
                }
            }
        }

        private fun createPackageDocument(): String {
            val doc = Jsoup.parse(TEMPLATE_PACKAGE, Parser.xmlParser())
            val rootElement = doc.selectFirst("package")!!
            rootElement.attr("unique-identifier", UNIQUE_IDENTIFIER)

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

        private fun createPackageSection(section: List<PackageElement>, root: Element): Element {
            return section.fold(root) { parent, element ->
                val attributes = element.attributes.toList()
                    .fold(Attributes()) { attributes, (key, value) -> attributes.add(key, value) }
                val child = Element(Tag.valueOf(element.tag), null, attributes)
                if (element.value != null) child.appendText(element.value)
                parent.appendChild(child)
            }
        }

        companion object {
            const val TEMPLATE_CONTAINER = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>"""

            const val TEMPLATE_PACKAGE = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0"/>"""
        }
    }
}

