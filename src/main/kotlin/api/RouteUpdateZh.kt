package api

import data.BookRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
private data class MetadataToTranslateDto(
    val title: String? = null,
    val introduction: String? = null,
    val toc: Map<Int, String>,
    val episodeIds: List<String>,
)

@Serializable
private data class MetadataTranslatedDto(
    val title: String? = null,
    val introduction: String? = null,
    val toc: Map<Int, String>,
)

@Serializable
@Resource("/update-zh")
private class UpdateZh {
    @Serializable
    @Resource("/metadata/{providerId}/{bookId}")
    data class Metadata(
        val parent: UpdateZh = UpdateZh(),
        val providerId: String,
        val bookId: String,
        val startIndex: Int = 0,
        val endIndex: Int = 65536,
    )

    @Serializable
    @Resource("/episode/{providerId}/{bookId}/{episodeId}")
    data class Episode(
        val parent: UpdateZh = UpdateZh(),
        val providerId: String,
        val bookId: String,
        val episodeId: String,
    )
}

fun Route.routeUpdateZh(bookRepo: BookRepository) {
    get<UpdateZh.Metadata> { loc ->
        val metadata = bookRepo.getMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
        )
        val episodeIds = metadata.toc
            .mapNotNull { it.episodeId }
            .safeSubList(loc.startIndex, loc.endIndex)
            .filter {
                val episode = bookRepo.getEpisodeInDb(
                    providerId = loc.providerId,
                    bookId = loc.bookId,
                    episodeId = it,
                )
                episode?.paragraphsZh == null
            }
        call.respond(
            MetadataToTranslateDto(
                title = if (metadata.titleZh == null) metadata.titleJp else null,
                introduction = if (metadata.introductionZh == null) metadata.introductionJp else null,
                toc = metadata.toc
                    .mapIndexedNotNull { index, it ->
                        if (it.titleZh == null) index to it.titleJp
                        else null
                    }.toMap(),
                episodeIds = episodeIds,
            )
        )
    }

    post<UpdateZh.Metadata> { loc ->
        val metadataTranslated = call.receive<MetadataTranslatedDto>()
        bookRepo.updateMetadata(
            providerId = loc.providerId,
            bookId = loc.bookId,
            titleZh = metadataTranslated.title,
            introductionZh = metadataTranslated.introduction,
            toc = metadataTranslated.toc,
        )
        call.respond("成功")
    }

    get<UpdateZh.Episode> { loc ->
        val episode = bookRepo.getEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
        )
        call.respond(
            if (episode.paragraphsZh != null) emptyList()
            else episode.paragraphsJp
        )
    }

    post<UpdateZh.Episode> { loc ->
        val episodeTranslated = call.receive<List<String>>()
        bookRepo.updateEpisode(
            providerId = loc.providerId,
            bookId = loc.bookId,
            episodeId = loc.episodeId,
            paragraphsZh = episodeTranslated,
        )
        call.respond("成功")
    }
}