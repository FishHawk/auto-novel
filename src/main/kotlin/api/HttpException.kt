package api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

data class HttpException(
    val status: HttpStatusCode,
    override val message: String?,
) : Exception()

fun <T> httpBadRequest(message: String?) =
    Result.failure<T>(HttpException(HttpStatusCode.BadRequest, message))

fun <T> httpUnauthorized(message: String?) =
    Result.failure<T>(HttpException(HttpStatusCode.Unauthorized, message))

fun <T> httpConflict(message: String?) =
    Result.failure<T>(HttpException(HttpStatusCode.Conflict, message))

fun <T> httpNotFound(message: String?) =
    Result.failure<T>(HttpException(HttpStatusCode.NotFound, message))

fun <T> httpInternalServerError(message: String?) =
    Result.failure<T>(HttpException(HttpStatusCode.InternalServerError, message))

suspend inline fun <reified T : Any> ApplicationCall.respondResult(result: Result<T>) {
    result.onSuccess {
        if (it is Unit) {
            response.status(HttpStatusCode.OK)
        } else {
            respond(it)
        }
    }.onFailure {
        val httpMethod = request.httpMethod.value
        val uri = request.uri
        application.environment.log.warn("已捕获异常 $httpMethod-$uri:", it)

        if (it is HttpException) {
            respond(it.status, it.message ?: it.status.description)
        } else {
            respond(HttpStatusCode.InternalServerError, it.message ?: "未知错误")
        }
    }
}
