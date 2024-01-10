package api

import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import infra.model.NovelFileLangV2
import infra.model.NovelFileTranslationsMode
import infra.model.TranslatorId
import infra.user.UserPersonalVolumeRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/personal")
private class UserPersonalVolumeRes {
    @Resource("/file/{volumeId}")
    class File(
        val parent: UserPersonalVolumeRes,
        val volumeId: String,
        val lang: NovelFileLangV2,
        val translationsMode: NovelFileTranslationsMode,
        val translations: List<TranslatorId>,
        val downloadToken: String,
        val filename: String,
    )
}

fun Route.routeUserPersonalVolume() {
    val service by inject<UserPersonalVolumeApi>()

    authenticateDb {
        get<UserPersonalVolumeRes> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.getUserVolumes(user = user)
            }
        }
    }
    get<UserPersonalVolumeRes.File> { loc ->
        call.tryRespondRedirect {
            val path = service.updateFile(
                volumeId = loc.volumeId,
                lang = loc.lang,
                translationsMode = loc.translationsMode,
                translations = loc.translations,
                downloadToken = loc.downloadToken,
            )
            val encodedFilename = loc.filename.encodeURLParameter(spaceToPlus = true)
            "../../../../../$path?filename=${encodedFilename}"
        }
    }
}

class UserPersonalVolumeApi(
    private val volumeRepo: UserPersonalVolumeRepository,
) {
    @Serializable
    data class PersonalVolumesDto(
        val downloadToken: String,
        val volumes: List<PersonalVolumeDto>,
    )

    @Serializable
    data class PersonalVolumeDto(
        val volumeId: String,
        val total: Int,
        val baidu: Int,
        val youdao: Int,
        val gpt: Int,
        val sakura: Int,
        val glossary: Map<String, String>,
    )

    suspend fun getUserVolumes(
        user: AuthenticatedUser,
    ): PersonalVolumesDto {
        val volumes = volumeRepo.list(user.id)
        return PersonalVolumesDto(
            downloadToken = user.id,
            volumes = volumes.map {
                PersonalVolumeDto(
                    volumeId = it.volumeId,
                    total = it.total,
                    baidu = it.baidu,
                    youdao = it.youdao,
                    gpt = it.gpt,
                    sakura = it.sakura,
                    glossary = emptyMap(),
                )
            },
        )
    }

    // File
    suspend fun updateFile(
        volumeId: String,
        lang: NovelFileLangV2,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
        downloadToken: String,
    ): String {
        if (!volumeId.endsWith("txt") && !volumeId.endsWith("epub"))
            throwBadRequest("不支持的文件格式")

        if (translations.isEmpty())
            throwBadRequest("没有设置翻译类型")

        if (lang == NovelFileLangV2.Jp)
            throwBadRequest("不支持的类型")

        val volume = volumeRepo.getVolume(
            userId = downloadToken,
            volumeId = volumeId,
        ) ?: throwNotFound("卷不存在")

        val newFileName = volume.makeTranslationVolumeFile(
            lang = lang,
            translationsMode = translationsMode,
            translations = translations.distinct(),
        )
        return "files-wenku/user-${downloadToken}/${volumeId.encodeURLPathPart()}.unpack/$newFileName"
    }
}
