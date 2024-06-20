package api.plugins

import api.throwUnauthorized
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import infra.user.User
import infra.user.UserRepository
import infra.user.UserRole
import infra.user.UserRole.Companion.toUserRole
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
import util.serialName
import kotlin.time.Duration.Companion.days

fun User.shouldBeAtLeast(role: UserRole) {
    if (!(this.role atLeast role)) {
        throwUnauthorized("只有${role.name}及以上的用户才有权限执行此操作")
    }
}

fun User.isOldAss(): Boolean {
    return Clock.System.now() - createdAt >= 30.days
}

fun User.shouldBeOldAss() {
    if (!isOldAss()) {
        throwUnauthorized("你还太年轻了")
    }
}

fun User.generateToken(
    secret: String,
): String = JWT
    .create()
    .apply {
        withClaim("id", id)
        withClaim("email", email)
        withClaim("username", username)
        withClaim("role", role.serialName())
        withClaim("createAt", createdAt.toJavaInstant())
        withExpiresAt((Clock.System.now() + 30.days).toJavaInstant())
    }
    .sign(Algorithm.HMAC256(secret))


fun Application.authentication(secret: String) = install(Authentication) {
    jwt {
        verifier(
            JWT.require(Algorithm.HMAC256(secret)).build()
        )
        validate { credential ->
            if (credential["id"] != null) {
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

fun ApplicationCall.user(): User =
    attributes[AuthenticatedUserKey]

fun ApplicationCall.userOrNull(): User? =
    attributes.getOrNull(AuthenticatedUserKey)

fun Route.authenticateDb(
    optional: Boolean = false,
    build: Route.() -> Unit,
): Route {
    return authenticate(
        strategy = if (optional) AuthenticationStrategy.Optional else AuthenticationStrategy.FirstSuccessful,
        build = build,
    ).apply {
        install(PostAuthenticationInterceptors)
    }
}

private val AuthenticatedUserKey = AttributeKey<User>("AuthenticatedUserKey")

private val PostAuthenticationInterceptors = createRouteScopedPlugin(name = "User Validator") {
    val userRepo = application.get<UserRepository>()

    on(AuthenticationChecked) { call ->
        call.principal<JWTPrincipal>()?.let { principal ->
            val id = principal["id"]!!
            val user = try {
                User(
                    id = id,
                    email = principal["email"]!!,
                    username = principal["username"]!!,
                    role = principal["role"]!!.toUserRole(),
                    createdAt = Instant.fromEpochMilliseconds(
                        principal.getClaim("createAt", Long::class)!!
                    ),
                )
            } catch (e: Throwable) {
                userRepo.getUser(id)!!
            }
            if (user.role === UserRole.Banned) {
                call.respond(HttpStatusCode.Unauthorized, "用户已被封禁")
            } else {
                call.attributes.put(AuthenticatedUserKey, user)
            }
        }
    }
}
