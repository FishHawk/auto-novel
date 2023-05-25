package util.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Attributes
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag

data class EpubResource(
    val path: String,
    val id: String,
    val mediaType: String,
    val properties: String? = null,
    val content: String,
)

private const val TEMPLATE_XHTML = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <head>
    <title/>
    <meta charset="utf-8"/>
  </head>
  <body />
</html>"""

data class Link(val href: String, val rel: String, val mediaType: String)

fun createEpubXhtml(
    path: String,
    id: String,
    language: String,
    title: String,
    links: List<Link> = emptyList(),
    properties: String? = null,
    bodyContent: (Element) -> Unit,
): EpubResource {
    val doc = Jsoup.parse(TEMPLATE_XHTML, Parser.xmlParser())
    doc.head().parentNode()!!.attributes()
        .add("lang", language)
        .add("xml:lang", language)
    doc.head().appendChildren(
        links.map {
            Element(
                Tag.valueOf("link"), null,
                Attributes()
                    .add("href", it.href)
                    .add("rel", it.rel)
                    .add("media-type", it.mediaType)
            )
        }
    )
    doc.title(title)
    bodyContent(doc.body())
    doc.outputSettings().prettyPrint(true)
    return EpubResource(
        path = path,
        id = id,
        mediaType = "application/xhtml+xml",
        properties = properties,
        content = doc.html()
    )
}

data class Navigation(
    val language: String,
    val title: String,
    val items: List<Item>,
) {
    data class Item(
        val href: String?,
        val text: String
    )
}

fun createEpubNav(
    navigation: Navigation
): EpubResource = createEpubXhtml(
    path = "Text/nav.xhtml",
    id = "nav.xhtml",
    language = navigation.language,
    title = navigation.title,
    links = emptyList(),
    properties = "nav"
) { body ->
    val nav = body.appendElement("nav")
        .attr("epub:type", "toc")
    nav.appendElement("h2").appendText(navigation.title)
    val ol = nav.appendElement("ol")
    navigation.items.forEach {
        val li = ol.appendElement("li")
        if (it.href == null) {
            li.appendElement("span")
                .appendText(it.text)
        } else {
            li.appendElement("a")
                .attr("href", it.href)
                .appendText(it.text)
        }
    }
}

private const val TEMPLATE_NCX = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head>
    <meta content="1" name="dtb:depth"/>
    <meta content="0" name="dtb:totalPageCount"/>
    <meta content="0" name="dtb:maxPageNumber"/>
</head>
</ncx>"""

fun createEpubNcx(
    identifier: String,
    navigation: Navigation,
): EpubResource {
    val doc = Jsoup.parse(TEMPLATE_NCX, Parser.xmlParser())
    val ncx = doc.selectFirst("ncx")!!
    val head = ncx.selectFirst("head")!!

    head.appendElement("meta")
        .attr("name", "dtb:uid")
        .attr("content", identifier)

    ncx.appendElement("docTitle")
        .appendElement("text")
        .appendText(navigation.title)

    val navMap = ncx.appendElement("navMap")
    navigation.items.forEachIndexed { index, it ->
        if (it.href != null) {
            val navPoint = navMap.appendElement("navPoint")
                .attr("id", "nav-$index")
            navPoint.appendElement("navLabel")
                .appendElement("text")
                .appendText(it.text)
            navPoint.appendElement("content")
                .attr("src", it.href)
        }
    }

    doc.outputSettings().prettyPrint(true)
    return EpubResource(
        path = "toc.ncx",
        id = "toc.ncx",
        mediaType = "application/x-dtbncx+xml",
        properties = null,
        content = doc.html()
    )
}