package api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import infra.common.UserRepository
import infra.model.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlin.time.Duration.Companion.days

data class AuthenticatedUser(
    val id: String,
    val username: String,
    val role: User.Role,
) {
    fun atLeastMaintainer(): Boolean {
        return role == User.Role.Maintainer || role == User.Role.Admin
    }

    fun generateToken(secret: String): Pair<String, Long> {
        val expiresAt = (Clock.System.now() + 30.days)
        return Pair(
            JWT.create()
                .apply {
                    withClaim("id", id)
                    withClaim("username", username)
                    if (role != User.Role.Normal) {
                        withClaim("role", role.toString())
                    }
                    withExpiresAt(expiresAt.toJavaInstant())
                }
                .sign(Algorithm.HMAC256(secret)),
            expiresAt.epochSeconds,
        )
    }
}

private fun JWTPrincipal.parseAuthenticatedUser(): AuthenticatedUser = payload.let { payload ->
    val id = payload.getClaim("id")
        .takeIf { !it.isMissing }
        ?.asString()
        ?: ""
    val username = payload.getClaim("username").asString()
    val role = payload.getClaim("role")
        .takeIf { !it.isMissing }
        ?.let { User.Role.valueOf(it.asString()) }
        ?: User.Role.Normal
    AuthenticatedUser(
        id = id,
        username = username,
        role = role,
    )
}

fun ApplicationCall.authenticatedUser(): AuthenticatedUser =
    principal<JWTPrincipal>()!!.parseAuthenticatedUser()

fun ApplicationCall.authenticatedUserOrNull(): AuthenticatedUser? =
    principal<JWTPrincipal>()?.parseAuthenticatedUser()

suspend fun AuthenticatedUser.compatEmptyUserId(userRepo: UserRepository): AuthenticatedUser {
    return if (id.isEmpty()) copy(id = userRepo.getByUsername(username)!!.id.toHexString())
    else this
}


fun AuthenticatedUser.shouldBeAtLeastMaintainer() {
    if (!atLeastMaintainer()) {
        throwUnauthorized("只有维护者及以上的用户才有权限执行此操作")
    }
}
