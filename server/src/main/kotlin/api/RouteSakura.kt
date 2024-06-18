package api

import api.plugins.*
import infra.sakura.SakuraFeedbackRepository
import infra.sakura.SakuraWebIncorrectCase
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/sakura")
private class SakuraRes {
    @Resource("/incorrect-case")
    class IncorrectCase(val parent: SakuraRes)
}

fun Route.routeSakura() {
    val api by inject<SakuraApi>()

    authenticateDb {
        post<SakuraRes.IncorrectCase> {
            @Serializable
            class Body(
                val providerId: String,
                val novelId: String,
                val chapterId: String,
                val jp: String,
                val zh: String,
                val contextJp: List<String>,
                val contextZh: List<String>,
            )

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                api.createSakuraWebIncorrectCase(
                    user = user,
                    providerId = body.providerId,
                    novelId = body.novelId,
                    chapterId = body.chapterId,
                    jp = body.jp,
                    zh = body.zh,
                    contextJp = body.contextJp,
                    contextZh = body.contextZh,
                )
            }
        }
    }
}

class SakuraApi(
    private val sakuraJobRepo: SakuraFeedbackRepository,
) {
    suspend fun createSakuraWebIncorrectCase(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        chapterId: String,
        jp: String,
        zh: String,
        contextJp: List<String>,
        contextZh: List<String>,
    ) {
        sakuraJobRepo.createWebIncorrectCase(
            SakuraWebIncorrectCase(
                providerId = providerId,
                novelId = novelId,
                chapterId = chapterId,
                uploader = user.username,
                jp = jp,
                zh = zh,
                contextJp = contextJp,
                contextZh = contextZh,
                createAt = Clock.System.now(),
            )
        )
    }
}