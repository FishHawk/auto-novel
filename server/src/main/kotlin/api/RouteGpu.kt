package api

import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import api.plugins.shouldBeAtLeastMaintainer
import infra.GpuWorkerManager
import infra.GpuWorkerProgress
import infra.common.GpuJobRepository
import infra.model.GpuJob
import infra.web.WebNovelMetadataRepository
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
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
import kotlin.time.Duration.Companion.days

@Resource("/gpu")
private class GpuRes {
    @Resource("/job")
    class Job(val parent: GpuRes) {
        @Resource("/{id}")
        class Id(val parent: Job, val id: String)
    }

    @Resource("/worker")
    class Worker(val parent: GpuRes)
}

fun Route.routeGpu() {
    val api by inject<GpuApi>()

    get<GpuRes> {
        call.tryRespond {
            api.getGpuInfo()
        }
    }

    authenticateDb {
        post<GpuRes.Job> {
            val user = call.authenticatedUser()
            val body = call.receive<String>()
            call.tryRespond {
                api.createGpuJob(
                    user = user,
                    task = body,
                )
            }
        }
        delete<GpuRes.Job.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                api.deleteGpuJob(user = user, id = loc.id)
            }
        }
    }
}

class GpuApi(
    private val gpuWorkerManager: GpuWorkerManager,
    private val gpuJobRepo: GpuJobRepository,
    private val webRepo: WebNovelMetadataRepository,
) {
    @Serializable
    data class GpuInfoDto(
        val jobs: List<GpuJobDto>,
        val workers: List<GpuWorkerDto>,
    )

    @Serializable
    data class GpuJobDto(
        val id: String,
        val task: String,
        val description: String,
        val workerUuid: String?,
        val submitter: String,
        @Contextual val createAt: Instant,
    )

    @Serializable
    data class GpuWorkerDto(
        val id: String,
        val active: Boolean,
        val card: String,
        val description: String,
        val progress: GpuWorkerProgress?,
    )

    suspend fun getGpuInfo(): GpuInfoDto {
        val jobs = gpuJobRepo
            .listJob()
            .map {
                GpuJobDto(
                    id = it.id.toHexString(),
                    task = it.task,
                    description = it.description,
                    workerUuid = it.workerId,
                    submitter = it.submitter,
                    createAt = it.createAt,
                )
            }
        val workers = gpuWorkerManager
            .workers
            .map {
                GpuWorkerDto(
                    id = it.id,
                    active = it.job.isActive,
                    card = it.card,
                    description = it.description,
                    progress = it.progress,
                )
            }
        return GpuInfoDto(
            jobs = jobs,
            workers = workers,
        )
    }

    suspend fun createGpuJob(
        user: AuthenticatedUser,
        task: String,
    ) {
        if ((Clock.System.now() - user.createdAt) < 30.days)
            throwUnauthorized("Sakura目前还在测试中，暂时只允许注册超过一个月的用户使用")

        val total = gpuJobRepo.countJob()
        if (total > 30) throwBadRequest("任务队列已满")

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

            else -> throwBadRequest("任务格式错误")
        }

        val isSuccess = gpuJobRepo.createJob(
            GpuJob(
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

    suspend fun deleteGpuJob(
        user: AuthenticatedUser,
        id: String,
    ) {
        val job = gpuJobRepo.getJob(ObjectId(id))
            ?: throwNotFound("任务不存在")

        if (job.submitter != user.username) {
            user.shouldBeAtLeastMaintainer()
        }

        val isSuccess = gpuJobRepo.deleteJob(ObjectId(id))
        if (!isSuccess) throwConflict("任务被占用")
    }
}