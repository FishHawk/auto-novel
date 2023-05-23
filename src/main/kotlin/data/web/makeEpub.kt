package data.web

import epub.EpubBook
import epub.Navigation
import epub.createEpubXhtml
import java.nio.file.Path

private const val MISSING_EPISODE_HINT = "该章节缺失。"

suspend fun makeEpubFile(
    filePath: Path,
    lang: BookFileLang,
    metadata: WebNovelMetadataRepository.NovelMetadata,
    episodes: Map<String, WebChapterRepository.NovelChapter>,
) {
    val epub = EpubBook()
    val identifier = "${metadata.providerId}.${metadata.novelId}"
    epub.addIdentifier(identifier, true)

    when (lang) {
        BookFileLang.JP -> {
            epub.addTitle(metadata.titleJp)
            epub.addLanguage("jp")
            epub.addDescription(metadata.introductionJp)
            epub.addNavigation(
                identifier,
                Navigation(
                    language = "jp",
                    title = metadata.titleJp,
                    items = tocToNavigationItems(metadata.toc) { it.titleJp }
                )
            )
        }

        else -> {
            epub.addTitle(metadata.titleZh ?: metadata.titleJp)
            epub.addLanguage("zh")
            epub.addDescription(metadata.introductionZh ?: "")
            epub.addNavigation(
                identifier,
                Navigation(
                    language = "zh",
                    title = metadata.titleZh ?: metadata.titleJp,
                    items = tocToNavigationItems(metadata.toc) { it.titleZh ?: it.titleJp }
                )
            )
        }
    }
    metadata.authors.map {
        epub.addCreator(it.name)
    }

    metadata.toc.filter { it.chapterId != null }.forEachIndexed { index, token ->
        val id = "episode${index + 1}.xhtml"
        val path = "Text/$id"
        val episode = episodes[token.chapterId]
        val resource = when (lang) {
            BookFileLang.JP -> createEpubXhtml(path, id, "jp", token.titleJp) {
                it.appendElement("h1").appendText(token.titleJp)
                if (episode == null) {
                    it.appendElement("p").appendText(MISSING_EPISODE_HINT)
                } else {
                    episode.paragraphs.forEach { text ->
                        it.appendElement("p").appendText(text)
                    }
                }
            }

            BookFileLang.ZH_BAIDU, BookFileLang.ZH_YOUDAO -> createEpubXhtml(
                path,
                id,
                "zh",
                token.titleZh ?: token.titleJp
            ) {
                it.appendElement("h1").appendText(token.titleZh ?: token.titleJp)
                val paragraphs = if (lang == BookFileLang.ZH_BAIDU) {
                    episode?.baiduParagraphs
                } else {
                    episode?.youdaoParagraphs
                }
                if (paragraphs == null) {
                    it.appendElement("p").appendText(MISSING_EPISODE_HINT)
                } else {
                    paragraphs.forEach { text ->
                        it.appendElement("p").appendText(text)
                    }
                }
            }

            BookFileLang.MIX_BAIDU, BookFileLang.MIX_YOUDAO -> createEpubXhtml(
                path,
                id,
                "zh",
                token.titleZh ?: token.titleJp
            ) {
                if (token.titleZh == null) {
                    it.appendElement("h1").appendText(token.titleJp)
                } else {
                    it.appendElement("h1").appendText(token.titleZh)
                    it.appendElement("p").appendText(token.titleJp)
                        .attr("style", "opacity:0.4;")
                }
                val paragraphs = if (lang == BookFileLang.MIX_BAIDU) {
                    episode?.baiduParagraphs
                } else {
                    episode?.youdaoParagraphs
                }
                if (episode == null || paragraphs == null) {
                    it.appendElement("p").appendText(MISSING_EPISODE_HINT)
                } else {
                    paragraphs.zip(episode.paragraphs).forEach { (textZh, textJp) ->
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
        epub.addResource(resource, true)
    }
    epub.write(filePath)
}

private fun tocToNavigationItems(
    toc: List<WebNovelMetadataRepository.NovelMetadata.TocItem>,
    title: (WebNovelMetadataRepository.NovelMetadata.TocItem) -> String
): List<Navigation.Item> {
    var index = 0
    return toc.map {
        if (it.chapterId != null) {
            index += 1
            Navigation.Item("episode$index.xhtml", title(it))
        } else {
            Navigation.Item(null, title(it))
        }
    }
}
