package api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import infra.EmailCodeRepository
import infra.UserRepository
import infra.model.User
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.Email
import util.PBKDF2
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Resource("/auth")
private class AuthRes {
    @Resource("/sign-in")
    class SignIn(val parent: AuthRes = AuthRes())

    @Resource("/sign-up")
    class SignUp(val parent: AuthRes = AuthRes())

    @Resource("/verify-email")
    class VerifyEmail(
        val parent: AuthRes = AuthRes(),
        val email: String,
    )

    @Resource("/reset-password")
    class ResetPassword(val parent: AuthRes = AuthRes())
}

fun Route.routeAuth() {
    val service by inject<AuthApi>()

    post<AuthRes.SignIn> {
        val body = call.receive<AuthApi.SignInBody>()
        val result = service.signIn(body)
        call.respondResult(result)
    }

    post<AuthRes.SignUp> {
        val body = call.receive<AuthApi.SignUpBody>()
        val result = service.signUp(body)
        call.respondResult(result)
    }

    post<AuthRes.VerifyEmail> { loc ->
        val result = service.verifyEmail(loc.email)
        call.respondResult(result)
    }

    post<AuthRes.ResetPassword> {
        val result = service.resetPassword()
        call.respondResult(result)
    }
}

class AuthApi(
    private val secret: String,
    private val userRepository: UserRepository,
    private val emailCodeRepository: EmailCodeRepository,
) {
    private fun generateToken(
        username: String,
        role: User.Role,
    ): Pair<String, Long> {
        val expiresAt = LocalDateTime.now()
            .plusMonths(6)
            .atZone(ZoneId.systemDefault())
        return Pair(
            JWT.create()
                .apply {
                    withClaim("username", username)
                    if (role != User.Role.Normal) {
                        withClaim("role", role.toString())
                    }
                    withExpiresAt(Date.from(expiresAt.toInstant()))
                }
                .sign(Algorithm.HMAC256(secret)),
            expiresAt.toEpochSecond(),
        )
    }

    @Serializable
    data class SignInBody(
        val emailOrUsername: String,
        val password: String,
    )

    @Serializable
    data class SignInDto(
        val email: String,
        val username: String,
        val role: User.Role,
        val token: String,
        val expiresAt: Long,
    )

    suspend fun signIn(body: SignInBody): Result<SignInDto> {
        val user = userRepository.getByEmail(body.emailOrUsername)
            ?: userRepository.getByUsername(body.emailOrUsername)
            ?: return httpNotFound("用户不存在")

        fun User.validatePassword(password: String): Boolean {
            return this.password == PBKDF2.hash(password, salt)
        }
        if (!user.validatePassword(body.password))
            return httpUnauthorized("密码错误")

        val (token, expiresAt) = generateToken(user.username, user.role)
        return Result.success(
            SignInDto(
                email = user.email,
                username = user.username,
                role = user.role,
                token = token,
                expiresAt = expiresAt,
            )
        )

    }

    @Serializable
    data class SignUpBody(
        val email: String,
        val emailCode: String,
        val username: String,
        val password: String,
    )

    suspend fun signUp(body: SignUpBody): Result<SignInDto> {
        if (body.username.length < 3) {
            return httpBadRequest("用户名至少为3个字符")
        }
        if (body.username.length > 15) {
            return httpBadRequest("用户名至多为15个字符")
        }
        if (body.password.length < 8) {
            return httpBadRequest("密码至少为8个字符")
        }
        userRepository.getByEmail(body.email)?.let {
            return httpConflict("邮箱已经被使用")
        }
        userRepository.getByUsername(body.username)?.let {
            return httpConflict("用户名已经被使用")
        }

        if (!emailCodeRepository.exist(body.email, body.emailCode))
            return httpBadRequest("邮箱验证码错误")

        userRepository.add(
            email = body.email,
            username = body.username,
            password = body.password,
        )

        val (token, expiresAt) = generateToken(body.username, User.Role.Normal)
        return Result.success(
            SignInDto(
                email = body.email,
                username = body.username,
                role = User.Role.Normal,
                token = token,
                expiresAt = expiresAt,
            )
        )
    }

    suspend fun verifyEmail(email: String): Result<String> {
        userRepository.getByEmail(email)?.let {
            return httpConflict("邮箱已经被使用")
        }

        try {
            InternetAddress(email).apply { validate() }
        } catch (e: AddressException) {
            return httpBadRequest("邮箱不合法")
        }

        val emailCode = String.format("%06d", Random().nextInt(999999))

        try {
            Email.send(
                to = email,
                subject = "$emailCode 日本网文机翻机器人 注册激活码",
                text = "您的注册激活码为 $emailCode\n" +
                        "激活码将会在15分钟后失效,请尽快完成注册\n" +
                        "这是系统邮件，请勿回复"
            )
        } catch (e: AddressException) {
            return httpInternalServerError("邮件发送失败")
        }

        emailCodeRepository.add(email, emailCode)
        return Result.success("邮件已发送")
    }

    suspend fun resetPassword(): Result<String> {
        // TODO
        return httpInternalServerError("未实现")
    }
}
