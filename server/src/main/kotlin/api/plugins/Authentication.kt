package api.plugins

import api.throwUnauthorized
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import infra.common.UserRepository
import infra.model.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.koin.ktor.ext.get
import kotlin.time.Duration.Companion.days

fun Application.authentication(secret: String) = install(Authentication) {
    jwt {
        verifier(
            JWT.require(Algorithm.HMAC256(secret)).build()
        )
        validate { credential ->
            if (credential["username"] != null) {
                JWTPrincipal(credential.payload)
            } else {
                null
            }
        }
        challenge { _, _ ->
            call.respond(HttpStatusCode.Unauthorized, "Token不合法或者过期")
        }
    }
}

data class AuthenticatedUser(
    val id: String,
    val username: String,
    val role: User.Role,
    val createdAt: Instant,
) {
    fun atLeastMaintainer(): Boolean {
        return role == User.Role.Maintainer || role == User.Role.Admin
    }
}

fun AuthenticatedUser.shouldBeAtLeastMaintainer() {
    if (!atLeastMaintainer()) {
        throwUnauthorized("只有维护者及以上的用户才有权限执行此操作")
    }
}

fun isOldAss(user: AuthenticatedUser?): Boolean {
    return user != null && Clock.System.now() - user.createdAt >= 30.days
}

fun shouldBeOldAss(user: AuthenticatedUser?) {
    if (!isOldAss(user)) {
        throwUnauthorized("你还太年轻了")
    }
}

fun generateToken(
    secret: String,
    id: String,
    username: String,
): Pair<String, Instant> {
    val expiresAt = (Clock.System.now() + 30.days)
    return Pair(
        JWT.create()
            .apply {
                withClaim("id", id)
                withClaim("username", username)
                withExpiresAt(expiresAt.toJavaInstant())
            }
            .sign(Algorithm.HMAC256(secret)),
        expiresAt,
    )
}

fun ApplicationCall.authenticatedUser(): AuthenticatedUser =
    attributes[AuthenticatedUserKey]

fun ApplicationCall.authenticatedUserOrNull(): AuthenticatedUser? =
    attributes.getOrNull(AuthenticatedUserKey)

fun Route.authenticateDb(
    optional: Boolean = false,
    build: Route.() -> Unit
): Route {
    return authenticate(
        strategy = if (optional) AuthenticationStrategy.Optional else AuthenticationStrategy.FirstSuccessful,
        build = build,
    ).apply {
        install(PostAuthenticationInterceptors)
    }
}

private val AuthenticatedUserKey = AttributeKey<AuthenticatedUser>("AuthenticatedUserKey")

private val PostAuthenticationInterceptors = createRouteScopedPlugin(name = "User Validator") {
    val userRepo = application.get<UserRepository>()

    on(AuthenticationChecked) { call ->
        call.principal<JWTPrincipal>()?.let { principal ->
            val username = principal["username"]!!
            val userDb = userRepo.getByUsername(username)!!
            val user = AuthenticatedUser(
                id = userDb.id.toHexString(),
                username = username,
                role = userDb.role,
                createdAt = userDb.createdAt,
            )
            if (userDb.role === User.Role.Banned) {
                call.respond(HttpStatusCode.Unauthorized, "用户已被封禁")
            } else {
                call.attributes.put(AuthenticatedUserKey, user)
            }
        }
    }
}
