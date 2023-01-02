package data.make

import data.BookTocItem
import epub.EpubBook
import epub.Navigation
import epub.createEpubResourceXhtml
import java.nio.file.Path

private const val MISSING_EPISODE_HINT = "该章节缺失。"

suspend fun makeEpubFile(filePath: Path, bookFile: BookFile) {
    val epub = EpubBook()
    val identifier = "${bookFile.metadata.providerId}.${bookFile.metadata.bookId}"
    epub.addIdentifier(identifier, true)

    when (bookFile.lang) {
        BookFile.Lang.JP -> {
            epub.addTitle(bookFile.metadata.titleJp)
            epub.addLanguage("jp")
            epub.addDescription(bookFile.metadata.introductionJp)
            epub.addNavigation(
                identifier,
                Navigation(
                    language = "jp",
                    title = bookFile.metadata.titleJp,
                    items = tocToNavigationItems(bookFile.metadata.toc) { it.titleJp }
                )
            )
        }

        else -> {
            epub.addTitle(bookFile.metadata.titleZh!!)
            epub.addLanguage("zh")
            epub.addDescription(bookFile.metadata.introductionZh!!)
            epub.addNavigation(
                identifier,
                Navigation(
                    language = "zh",
                    title = bookFile.metadata.titleZh,
                    items = tocToNavigationItems(bookFile.metadata.toc) { it.titleZh ?: it.titleJp }
                )
            )
        }
    }
    bookFile.metadata.authors.map {
        epub.addCreator(it.name)
    }

    bookFile.metadata.toc.filter { it.episodeId != null }.forEachIndexed { index, token ->
        val id = "episode${index + 1}"
        val path = "$id.xhtml"
        val episode = bookFile.episodes[token.episodeId]
        val resource = when (bookFile.lang) {
            BookFile.Lang.JP -> createEpubResourceXhtml(path, "jp", token.titleJp) {
                it.appendElement("h1").appendText(token.titleJp)
                if (episode == null) {
                    it.appendElement("p").appendText(MISSING_EPISODE_HINT)
                } else {
                    episode.paragraphsJp.forEach { text ->
                        it.appendElement("p").appendText(text)
                    }
                }
            }

            BookFile.Lang.ZH -> createEpubResourceXhtml(path, "zh", token.titleZh ?: token.titleJp) {
                it.appendElement("h1").appendText(token.titleZh ?: token.titleJp)
                if (episode?.paragraphsZh == null) {
                    it.appendElement("p").appendText(MISSING_EPISODE_HINT)
                } else {
                    episode.paragraphsZh.forEach { text ->
                        it.appendElement("p").appendText(text)
                    }
                }
            }

            BookFile.Lang.MIX -> createEpubResourceXhtml(path, "zh", token.titleZh ?: token.titleJp) {
                if (token.titleZh == null) {
                    it.appendElement("h1").appendText(token.titleJp)
                } else {
                    it.appendElement("h1").appendText(token.titleZh)
                    it.appendElement("p").appendText(token.titleJp)
                        .attr("style", "opacity:0.4;")
                }
                if (episode?.paragraphsZh == null) {
                    it.appendElement("p").appendText(MISSING_EPISODE_HINT)
                } else {
                    episode.paragraphsZh.zip(episode.paragraphsJp).forEach { (textZh, textJp) ->
                        if (textJp.isBlank()) {
                            it.appendElement("p").appendText(textJp)
                        } else {
                            it.appendElement("p").appendText(textZh.trimEnd())
                            it.appendElement("p").appendText(textJp.trimStart())
                                .attr("style", "opacity:0.4;")
                        }
                    }
                }
            }
        }
        epub.addResource(id, resource, true)
    }
    epub.write(filePath)
}

private fun tocToNavigationItems(
    toc: List<BookTocItem>,
    title: (BookTocItem) -> String
): List<Navigation.Item> {
    var index = 0
    return toc.map {
        if (it.episodeId != null) {
            index += 1
            Navigation.Item("episode$index.xhtml", title(it))
        } else {
            Navigation.Item(null, title(it))
        }
    }
}
