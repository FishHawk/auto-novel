package api

import api.plugins.AuthenticatedUser
import api.plugins.RateLimitNames
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import infra.VolumeCreateException
import infra.model.NovelFileLangV2
import infra.model.NovelFileTranslationsMode
import infra.model.TranslatorId
import infra.model.WenkuNovelVolumeJp
import infra.personal.PersonalNovelVolumeRepository
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

@Resource("/personal")
private class PersonalNovelRes {
    @Resource("/volume/{volumeId}")
    class Volume(val parent: PersonalNovelRes, val volumeId: String)

    @Resource("/translate/{translatorId}/{volumeId}")
    class Translate(val parent: PersonalNovelRes, val translatorId: TranslatorId, val volumeId: String) {
        @Resource("/{chapterId}")
        class Chapter(val parent: Translate, val chapterId: String)
    }

    @Resource("/file/{volumeId}")
    class File(
        val parent: PersonalNovelRes,
        val volumeId: String,
        val lang: NovelFileLangV2,
        val translationsMode: NovelFileTranslationsMode,
        val translations: List<TranslatorId>,
    )
}

fun Route.routePersonalNovel() {
    val service by inject<PersonalNovelApi>()

    authenticateDb {
        get<PersonalNovelRes> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.getUserVolumes(user = user)
            }
        }

        rateLimit(RateLimitNames.CreateWenkuVolume) {
            post<PersonalNovelRes.Volume> { loc ->
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
        delete<PersonalNovelRes.Volume> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.deleteVolume(
                    user = user,
                    userId = user.id,
                    volumeId = loc.volumeId,
                )
            }
        }

        // Translate
        get<PersonalNovelRes.Translate> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.getTranslateTask(
                    user = user,
                    userId = user.id,
                    translatorId = loc.translatorId,
                    volumeId = loc.volumeId,
                )
            }
        }
        get<PersonalNovelRes.Translate.Chapter> { loc ->
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
        put<PersonalNovelRes.Translate.Chapter> { loc ->
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

        // File
        get<PersonalNovelRes.File> { loc ->
            val user = call.authenticatedUser()
            call.tryRespondRedirect {
                val path = service.updateFile(
                    user = user,
                    userId = user.id,
                    volumeId = loc.volumeId,
                    lang = loc.lang,
                    translationsMode = loc.translationsMode,
                    translations = loc.translations,
                )
                "../../../../../../$path"
            }
        }
    }
}

class PersonalNovelApi(
    private val volumeRepo: PersonalNovelVolumeRepository,
) {
    suspend fun getUserVolumes(
        user: AuthenticatedUser,
    ): List<WenkuNovelVolumeJp> {
        return volumeRepo.list(user.id)
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
        userId: String,
        translatorId: TranslatorId,
        volumeId: String,
    ): TranslateTaskDto {
        if (user.id !== userId)
            throwUnauthorized("没有权限")

        if (translatorId == TranslatorId.Sakura)
            throwBadRequest("Sakura不支持浏览器翻译")
        validateVolumeId(volumeId)

        val volume = volumeRepo.getVolume(
            userId = userId,
            volumeId = volumeId,
        ) ?: throwNotFound("卷不存在")

        val untranslatedChapterIds = mutableListOf<String>()
        val expiredChapterIds = mutableListOf<String>()
        volume.listChapter().forEach {
            if (!volume.translationExist(translatorId, it)) {
                untranslatedChapterIds.add(it)
            } else if (
                false
//                volume.getChapterGlossary(translatorId, it)?.uuid != novel?.glossaryUuid
            ) {
                expiredChapterIds.add(it)
            }
        }
        return TranslateTaskDto(
            glossaryUuid = null,
            glossary = emptyMap(),
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

        if (translatorId == TranslatorId.Sakura)
            throwBadRequest("Sakura不支持浏览器翻译")
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
        user: AuthenticatedUser,
        userId: String,
        volumeId: String,
        lang: NovelFileLangV2,
        translationsMode: NovelFileTranslationsMode,
        translations: List<TranslatorId>,
    ): String {
        if (user.id !== userId)
            throwUnauthorized("没有权限")

        validateVolumeId(volumeId)

        if (translations.isEmpty())
            throwBadRequest("没有设置翻译类型")

        if (lang == NovelFileLangV2.Jp)
            throwBadRequest("不支持的类型")

        val volume = volumeRepo.getVolume(
            userId = userId,
            volumeId = volumeId,
        ) ?: throwNotFound("卷不存在")

        val newFileName = volume.makeTranslationVolumeFile(
            lang = lang,
            translationsMode = translationsMode,
            translations = translations.distinct(),
        )
        return "files-wenku/user-${userId}/${volumeId.encodeURLPathPart()}.unpack/$newFileName"
    }
}
