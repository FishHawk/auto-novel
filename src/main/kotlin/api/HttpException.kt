package api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

data class HttpException(
    val status: HttpStatusCode,
    override val message: String,
) : Exception()

fun <T> httpNotFound(message: String?) =
    Result.failure<T>(HttpException(HttpStatusCode.NotFound, message ?: "未找到"))

fun <T> httpInternalServerError(message: String?) =
    Result.failure<T>(HttpException(HttpStatusCode.InternalServerError, message ?: "内部错误"))

suspend inline fun <reified T : Any> ApplicationCall.respondResult(result: Result<T>) {
    result.onSuccess {
        if (it is Unit) {
            response.status(HttpStatusCode.OK)
        } else {
            respond(it)
        }
    }.onFailure {
        application.environment.log.info("已捕获异常:", it)
        if (it is HttpException) {
            respond(it.status, it.message)
        } else {
            respond(HttpStatusCode.InternalServerError, it.message ?: "未知错误")
        }
    }
}
