package api

import api.plugins.*
import infra.common.SakuraJobRepository
import infra.model.SakuraJob
import infra.model.SakuraWebIncorrectCase
import infra.model.User
import infra.web.WebNovelMetadataRepository
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject
import sakura.SakuraWorkerManager
import sakura.SakuraWorkerProgress

@Resource("/sakura")
private class SakuraRes {
    @Resource("/job")
    class Job(val parent: SakuraRes) {
        @Resource("/{id}")
        class Id(val parent: Job, val id: String)
    }

    @Resource("/worker")
    class Worker(val parent: SakuraRes) {
        @Resource("/{id}")
        class Id(val parent: Worker, val id: String) {
            @Resource("/start")
            class Start(val parent: Id)

            @Resource("/stop")
            class Stop(val parent: Id)
        }
    }

    @Resource("/incorrect-case")
    class IncorrectCase(val parent: SakuraRes)
}

fun Route.routeSakura() {
    val api by inject<SakuraApi>()

    authenticateDb(optional = true) {
        get<SakuraRes> {
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                api.getSakuraStatus(user)
            }
        }
    }

    authenticateDb {
        rateLimit(RateLimitNames.CreateSakuraJob) {
            post<SakuraRes.Job> {
                val user = call.authenticatedUser()
                val body = call.receive<String>()
                call.tryRespond {
                    api.createSakuraJob(
                        user = user,
                        task = body,
                    )
                }
            }
        }
        delete<SakuraRes.Job.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                api.deleteSakuraJob(user = user, id = loc.id)
            }
        }

        post<SakuraRes.Worker> {
            @Serializable
            class Body(
                val gpu: String,
                val endpoint: String,
            )

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                api.createSakuraWorker(
                    user = user,
                    gpu = body.gpu,
                    endpoint = body.endpoint,
                )
            }
        }
        delete<SakuraRes.Worker.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                api.deleteSakuraWorker(user = user, id = loc.id)
            }
        }
        post<SakuraRes.Worker.Id.Start> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                api.startSakuraWorker(user = user, id = loc.parent.id)
            }
        }
        post<SakuraRes.Worker.Id.Stop> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                api.stopSakuraWorker(user = user, id = loc.parent.id)
            }
        }

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
    private val sakuraWorkerManager: SakuraWorkerManager,
    private val sakuraJobRepo: SakuraJobRepository,
    private val webRepo: WebNovelMetadataRepository,
    private val wenkuNovelRepo: WenkuNovelMetadataRepository,
    private val wenkuVolumeRepo: WenkuNovelVolumeRepository,
) {
    @Serializable
    data class SakuraStatusDto(
        val jobs: List<SakuraJobDto>,
        val workers: List<SakuraWorkerDto>,
    )

    @Serializable
    data class SakuraJobDto(
        val id: String,
        val task: String,
        val description: String,
        val workerId: String?,
        val submitter: String,
        @Contextual val createAt: Instant,
    )

    @Serializable
    data class SakuraWorkerDto(
        val id: String,
        val username: String,
        val active: Boolean,
        val endpoint: String?,
        val gpu: String,
        val description: String,
        val progress: SakuraWorkerProgress?,
    )

    suspend fun getSakuraStatus(
        user: AuthenticatedUser?,
    ): SakuraStatusDto {
        val jobs = sakuraJobRepo
            .listJob()
            .map {
                SakuraJobDto(
                    id = it.id.toHexString(),
                    task = it.task,
                    description = it.description,
                    workerId = it.workerId,
                    submitter = it.submitter,
                    createAt = it.createAt,
                )
            }
        val workers = sakuraWorkerManager
            .workers
            .values
            .map {
                val endpoint = when {
                    user == null -> null
                    user.username == it.username -> it.endpoint
                    user.role atLeast User.Role.Admin -> it.endpoint
                    else -> null
                }
                SakuraWorkerDto(
                    id = it.id,
                    username = it.username,
                    active = it.isActive,
                    endpoint = endpoint,
                    gpu = it.gpu,
                    description = it.description,
                    progress = it.progress,
                )
            }
        return SakuraStatusDto(
            jobs = jobs,
            workers = workers,
        )
    }

    suspend fun createSakuraJob(
        user: AuthenticatedUser,
        task: String,
    ) {
        val total = sakuraJobRepo.countJob()
        if (total >= 150) throwBadRequest("任务队列已满")

        val similarJobCount = sakuraJobRepo.countSimilarJob(task)
        if (similarJobCount >= 2) throwBadRequest("同一部小说最多只能有两个任务在排队")

        val taskUrl = try {
            URLBuilder().takeFrom(task).build()
        } catch (e: Throwable) {
            throwBadRequest("任务格式错误")
        }

        val description = when (taskUrl.pathSegments.first()) {
            "web" -> {
                if (taskUrl.pathSegments.size != 3) throwBadRequest("任务格式错误")
                val (_, providerId, novelId) = taskUrl.pathSegments
                val novel = webRepo.get(providerId, novelId)
                    ?: throwNotFound("小说不存在")
                novel.titleJp
            }

            "wenku" -> {
                if (taskUrl.pathSegments.size != 3) throwBadRequest("任务格式错误")
                val (_, novelId, volumeId) = taskUrl.pathSegments
                val novel = wenkuNovelRepo.get(novelId)
                    ?: throwNotFound("小说不存在")
                wenkuVolumeRepo.getVolume(novelId, volumeId)
                    ?: throwNotFound("卷不存在")
                novel.title
            }

            else -> throwBadRequest("任务格式错误")
        }

        val isSuccess = sakuraJobRepo.createJob(
            SakuraJob(
                id = ObjectId(),
                submitter = user.username,
                workerId = null,
                task = task,
                description = description,
                createAt = Clock.System.now(),
            )
        )
        if (!isSuccess) throwConflict("任务已存在")
    }

    suspend fun deleteSakuraJob(
        user: AuthenticatedUser,
        id: String,
    ) {
        val job = sakuraJobRepo.getJob(ObjectId(id))
            ?: throwNotFound("任务不存在")

        if (job.submitter != user.username) {
            user.shouldBeAtLeast(User.Role.Admin)
        }

        val isSuccess = sakuraJobRepo.deleteJob(ObjectId(id))
        if (!isSuccess) throwConflict("任务被占用")
    }

    suspend fun createSakuraWorker(
        user: AuthenticatedUser,
        gpu: String,
        endpoint: String,
    ) {
        user.shouldBeAtLeast(User.Role.Maintainer)
        sakuraWorkerManager.createWorker(
            username = user.username,
            gpu = gpu,
            endpoint = endpoint,
        )
    }

    suspend fun deleteSakuraWorker(
        user: AuthenticatedUser,
        id: String,
    ) {
        user.shouldBeAtLeast(User.Role.Maintainer)
        val worker = sakuraWorkerManager.workers[id]
            ?: throwNotFound("Sakura worker不存在")
        if (user.username != worker.username) {
            user.shouldBeAtLeast(User.Role.Admin)
        }
        sakuraWorkerManager.deleteWorker(id)
    }

    suspend fun startSakuraWorker(
        user: AuthenticatedUser,
        id: String,
    ) {
        user.shouldBeAtLeast(User.Role.Maintainer)
        val worker = sakuraWorkerManager.workers[id]
            ?: throwNotFound("Sakura worker不存在")
        if (user.username != worker.username) {
            user.shouldBeAtLeast(User.Role.Admin)
        }
        sakuraWorkerManager.startWorker(id)
    }

    suspend fun stopSakuraWorker(user: AuthenticatedUser, id: String) {
        user.shouldBeAtLeast(User.Role.Maintainer)
        val worker = sakuraWorkerManager.workers[id]
            ?: throwNotFound("Sakura worker不存在")
        if (user.username != worker.username) {
            user.shouldBeAtLeast(User.Role.Admin)
        }
        sakuraWorkerManager.stopWorker(id)
    }

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