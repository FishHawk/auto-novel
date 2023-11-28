package api

import api.plugins.AuthenticatedUser
import api.plugins.RateLimitNames
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import infra.VolumeCreateException
import infra.model.NovelFileLangV2
import infra.model.NovelFileTranslationsMode
import infra.model.TranslatorId
import infra.user.UserPersonalVolumeRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.io.InputStream
import java.util.*

@Resource("/personal")
private class UserPersonalVolumeRes {
    @Resource("/volume/{volumeId}")
    class Volume(
        val parent: UserPersonalVolumeRes,
        val volumeId: String,
    ) {
        @Resource("/glossary")
        class Glossary(val parent: Volume)
    }

    @Resource("/translate/{translatorId}/{volumeId}")
    class Translate(
        val parent: UserPersonalVolumeRes,
        val translatorId: TranslatorId,
        val volumeId: String,
    ) {
        @Resource("/{chapterId}")
        class Chapter(
            val parent: Translate,
            val chapterId: String
        )
    }

    @Resource("/file/{volumeId}")
    class File(
        val parent: UserPersonalVolumeRes,
        val volumeId: String,
        val lang: NovelFileLangV2,
        val translationsMode: NovelFileTranslationsMode,
        val translations: List<TranslatorId>,
        val downloadToken: String,
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

        rateLimit(RateLimitNames.CreateWenkuVolume) {
            post<UserPersonalVolumeRes.Volume> { loc ->
                suspend fun MultiPartData.firstFilePart(): PartData.FileItem? {
                    while (true) {
                        val part = readPart() ?: return null
                        if (part is PartData.FileItem) return part
                        else part.dispose()
                    }
                }

                val user = call.authenticatedUser()
                val filePart = call.receiveMultipart().firstFilePart()
                call.tryRespond {
                    if (filePart == null) throwBadRequest("请求里没有文件")
                    service.createVolume(
                        user = user,
                        userId = user.id,
                        volumeId = loc.volumeId,
                        inputStream = filePart.streamProvider(),
                    )
                }
            }
        }
        delete<UserPersonalVolumeRes.Volume> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteVolume(
                    user = user,
                    userId = user.id,
                    volumeId = loc.volumeId,
                )
            }
        }

        put<UserPersonalVolumeRes.Volume.Glossary> { loc ->
            val user = call.authenticatedUser()
            val body = call.receive<Map<String, String>>()
            call.tryRespond {
                service.updateGlossary(
                    user = user,
                    volumeId = loc.parent.volumeId,
                    glossary = body
                )
            }
        }

        // Translate
        get<UserPersonalVolumeRes.Translate> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.getTranslateTask(
                    user = user,
                    translatorId = loc.translatorId,
                    volumeId = loc.volumeId,
                )
            }
        }
        get<UserPersonalVolumeRes.Translate.Chapter> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.getChapterToTranslate(
                    user = user,
                    userId = user.id,
                    volumeId = loc.parent.volumeId,
                    chapterId = loc.chapterId,
                )
            }
        }
        put<UserPersonalVolumeRes.Translate.Chapter> { loc ->
            @Serializable
            class Body(
                val glossaryUuid: String? = null,
                val paragraphsZh: List<String>,
            )

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.updateChapterTranslation(
                    user = user,
                    userId = user.id,
                    translatorId = loc.parent.translatorId,
                    volumeId = loc.parent.volumeId,
                    chapterId = loc.chapterId,
                    glossaryUuid = body.glossaryUuid,
                    paragraphsZh = body.paragraphsZh,
                )
            }
        }
    }

    // File
    get<UserPersonalVolumeRes.File> { loc ->
        call.tryRespondRedirect {
            val path = service.updateFile(
                volumeId = loc.volumeId,
                lang = loc.lang,
                translationsMode = loc.translationsMode,
                translations = loc.translations,
                downloadToken = loc.downloadToken,
            )
            "../../../../../$path"
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
                    glossary = volumeRepo
                        .getVolume(user.id, it.volumeId)!!
                        .getVolumeGlossary()
                        ?.glossary ?: emptyMap(),
                )
            },
        )
    }

    private fun validateVolumeId(volumeId: String) {
        if (!volumeId.endsWith("txt") && !volumeId.endsWith("epub"))
            throwBadRequest("不支持的文件格式")
    }

    suspend fun createVolume(
        user: AuthenticatedUser,
        userId: String,
        volumeId: String,
        inputStream: InputStream,
    ) {
        if (user.id != userId)
            throwUnauthorized("没有权限")

        validateVolumeId(volumeId)

        try {
            volumeRepo.createVolume(
                userId = user.id,
                volumeId = volumeId,
                inputStream = inputStream,
            )
        } catch (e: VolumeCreateException) {
            when (e) {
                is VolumeCreateException.VolumeAlreadyExist -> throwConflict("卷已经存在")
                is VolumeCreateException.VolumeUnpackFailure -> throwInternalServerError("解包失败,由于${e.cause?.message}")
            }
        }
    }

    suspend fun deleteVolume(
        user: AuthenticatedUser,
        userId: String,
        volumeId: String,
    ) {
        if (user.id !== userId)
            throwUnauthorized("没有权限")

        validateVolumeId(volumeId)
        volumeRepo.deleteVolume(
            userId = userId,
            volumeId = volumeId,
        )
    }

    suspend fun updateGlossary(
        user: AuthenticatedUser,
        volumeId: String,
        glossary: Map<String, String>,
    ) {
        val volume = volumeRepo.getVolume(
            userId = user.id,
            volumeId = volumeId,
        ) ?: throwNotFound("卷不存在")

        if (volume.getVolumeGlossary()?.glossary == glossary)
            throwBadRequest("修改为空")

        volume.setVolumeGlossary(
            glossaryUuid = UUID.randomUUID().toString(),
            glossary = glossary,
        )
    }

    // Translate
    @Serializable
    data class TranslateTaskDto(
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val untranslatedChapters: List<String>,
        val expiredChapters: List<String>,
    )

    suspend fun getTranslateTask(
        user: AuthenticatedUser,
        translatorId: TranslatorId,
        volumeId: String,
    ): TranslateTaskDto {
        validateVolumeId(volumeId)

        val volume = volumeRepo.getVolume(
            userId = user.id,
            volumeId = volumeId,
        ) ?: throwNotFound("卷不存在")

        val glossary = volume.getVolumeGlossary()

        val untranslatedChapterIds = mutableListOf<String>()
        val expiredChapterIds = mutableListOf<String>()
        volume.listChapter().forEach {
            if (!volume.translationExist(translatorId, it)) {
                untranslatedChapterIds.add(it)
            } else if (
                volume.getChapterGlossary(translatorId, it)?.uuid != glossary?.uuid
            ) {
                expiredChapterIds.add(it)
            }
        }
        return TranslateTaskDto(
            glossaryUuid = glossary?.uuid,
            glossary = glossary?.glossary ?: emptyMap(),
            untranslatedChapters = untranslatedChapterIds,
            expiredChapters = expiredChapterIds,
        )
    }

    suspend fun getChapterToTranslate(
        user: AuthenticatedUser,
        userId: String,
        volumeId: String,
        chapterId: String,
    ): List<String> {
        if (user.id !== userId)
            throwUnauthorized("没有权限")

        validateVolumeId(volumeId)

        val volume = volumeRepo.getVolume(
            userId = userId,
            volumeId = volumeId,
        )
            ?: throwNotFound("卷不存在")
        return volume.getChapter(chapterId)
            ?: throwNotFound("章节不存在")
    }

    suspend fun updateChapterTranslation(
        user: AuthenticatedUser,
        userId: String,
        translatorId: TranslatorId,
        volumeId: String,
        chapterId: String,
        glossaryUuid: String?,
        paragraphsZh: List<String>,
    ): Int {
        if (user.id !== userId)
            throwUnauthorized("没有权限")

        validateVolumeId(volumeId)

        val volume = volumeRepo.getVolume(
            userId = userId,
            volumeId = volumeId,
        ) ?: throwNotFound("卷不存在")

        val jpLines = volume.getChapter(chapterId)
            ?: throwNotFound("章节不存在")

        if (jpLines.size != paragraphsZh.size)
            throwBadRequest("翻译行数不匹配")

        volume.setTranslation(
            translatorId = translatorId,
            chapterId = chapterId,
            lines = paragraphsZh,
        )
        if (glossaryUuid != null) {
            volume.setChapterGlossary(
                translatorId = translatorId,
                chapterId = chapterId,
                glossaryUuid = glossaryUuid,
                glossary = emptyMap(),
            )
        }

        return volume.listTranslation(
            translatorId = translatorId,
        ).size
    }

    // File
    suspend fun updateFile(
        volumeId: String,
        lang: NovelFileLangV2,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
        downloadToken: String,
    ): String {
        validateVolumeId(volumeId)

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
