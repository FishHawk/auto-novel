package api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.jwtUsername(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("username").asString()

fun ApplicationCall.jwtUsernameOrNull(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("username")?.asString()
