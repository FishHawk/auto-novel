package data.provider.providers

import data.provider.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*

class Alphapolib : BookProvider {
    companion object {
        const val id = "alphapolib"
    }

    val clientText = HttpClient(Java) {
        install(HttpCookies) { storage = cookies }
        expectSuccess = true
        engine {
            proxy = ProxyBuilder.http("http://127.0.0.1:7890")
        }
    }

    override suspend fun getRank(options: Map<String, String>): List<SBookListItem> {
        TODO("Not yet implemented")
    }

    private fun getMetadataUrl(bookId: String): String {
        return "https://www.alphapolis.co.jp/novel/" + bookId.split('-').joinToString("/")
    }

    private fun getEpisodeUrl(bookId: String, episodeId: String): String {
        return getMetadataUrl(bookId) + "/episode/" + episodeId
    }

    override suspend fun getMetadata(bookId: String): SBookMetadata {
        val link = "https://www.alphapolis.co.jp/novel/638978238/525733370"
        val doc = clientText.get(link).document()
        val main = doc.selectFirst("div.content-main")!!
        val title = main.selectFirst("h1.title")!!.text()
        val author = main
            .selectFirst("div.author")!!
            .selectFirst("a")!!
            .let {
                SBookAuthor(
                    name = it.text(),
                    link = "https://www.alphapolis.co.jp" + it.attr("href"),
                )
            }
        val introduction = main.selectFirst("div.abstract")!!.text()
        val toc = doc
            .select("div.episodes > div.episode")
            .map {
                SBookTocItem(
                    title = it.selectFirst("span.title")!!.text(),
                    episodeId = it.selectFirst("a")!!.attr("href").substringAfterLast("/"),
                )
            }

        return SBookMetadata(
            title = title,
            authors = listOf(author),
            introduction = introduction,
            toc = toc,
        )
    }

    override suspend fun getEpisode(bookId: String, episodeId: String): SBookEpisode {
        val doc = clientText.get(getEpisodeUrl(bookId, episodeId)).document()
        // TODO cookie怎么生成的？
        // doc.selectFirst("div#novelBody")!!.text()
        return SBookEpisode(paragraphs = emptyList())
    }
}
