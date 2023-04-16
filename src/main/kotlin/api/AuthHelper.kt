package api

import com.auth0.jwt.interfaces.Payload
import data.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

data class JwtUser(
    val username: String,
    val role: User.Role,
) {
    fun atLeastMaintainer(): Boolean {
        return role == User.Role.Maintainer || role == User.Role.Admin
    }
}

private fun JWTPrincipal.toJwtUser(): JwtUser = payload.let { payload ->
    val username = payload.getClaim("username").asString()
    val role = payload.getClaim("role")
        .takeIf { !it.isNull }
        ?.let { User.Role.valueOf(it.asString()) }
        ?: User.Role.Normal
    JwtUser(username, role)
}

fun ApplicationCall.jwtUser(): JwtUser =
    principal<JWTPrincipal>()!!.toJwtUser()

fun ApplicationCall.jwtUserOrNull(): JwtUser? =
    principal<JWTPrincipal>()?.toJwtUser()