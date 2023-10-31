package api

import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import api.plugins.generateToken
import infra.common.UserRepository
import infra.model.User
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.Email
import util.PBKDF2
import java.util.*

@Resource("/auth")
private class AuthRes {
    @Resource("/sign-in")
    class SignIn(val parent: AuthRes)

    @Resource("/renew")
    class Renew(val parent: AuthRes)

    @Resource("/sign-up")
    class SignUp(val parent: AuthRes)

    @Resource("/verify-email")
    class VerifyEmail(val parent: AuthRes, val email: String)

    @Resource("/reset-password-email")
    class ResetPasswordEmail(val parent: AuthRes, val emailOrUsername: String)

    @Resource("/reset-password")
    class ResetPassword(val parent: AuthRes, val emailOrUsername: String)
}

fun Route.routeAuth() {
    val service by inject<AuthApi>()

    post<AuthRes.SignIn> {
        @Serializable
        class Body(
            val emailOrUsername: String,
            val password: String,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.signIn(
                emailOrUsername = body.emailOrUsername,
                password = body.password,
            )
        }
    }
    authenticateDb {
        get<AuthRes.Renew> {
            val user = call.authenticatedUser()
            call.tryRespond {
                service.renew(user)
            }
        }
    }

    post<AuthRes.SignUp> {
        @Serializable
        class Body(
            val email: String,
            val emailCode: String,
            val username: String,
            val password: String,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.signUp(
                email = body.email,
                emailCode = body.emailCode,
                username = body.username,
                password = body.password,
            )
        }
    }
    post<AuthRes.VerifyEmail> { loc ->
        call.tryRespond {
            service.sendVerifyEmail(loc.email)
        }
    }

    post<AuthRes.ResetPasswordEmail> { loc ->
        call.tryRespond {
            service.sendResetPasswordTokenEmail(loc.emailOrUsername)
        }
    }
    post<AuthRes.ResetPassword> { loc ->
        @Serializable
        class Body(
            val token: String,
            val password: String,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.resetPassword(
                emailOrUsername = loc.emailOrUsername,
                token = body.token,
                password = body.password,
            )
        }
    }
}

class AuthApi(
    private val secret: String,
    private val userRepo: UserRepository,
) {
    private fun throwUserNotFound(): Nothing =
        throwNotFound("用户不存在")

    @Serializable
    data class SignInDto(
        val username: String,
        val role: User.Role,
        val token: String,
        @Contextual val createAt: Instant,
        @Contextual val expiresAt: Instant,
    )

    suspend fun signIn(
        emailOrUsername: String,
        password: String,
    ): SignInDto {
        val user = userRepo.getByEmail(emailOrUsername)
            ?: userRepo.getByUsername(emailOrUsername)
            ?: throwUserNotFound()

        fun User.validatePassword(password: String): Boolean {
            return this.password == PBKDF2.hash(password, salt)
        }
        if (!user.validatePassword(password))
            throwUnauthorized("密码错误")

        val (token, expiresAt) = generateToken(
            secret = secret,
            id = user.id.toHexString(),
            username = user.username,
        )

        return SignInDto(
            username = user.username,
            role = user.role,
            token = token,
            createAt = user.createdAt,
            expiresAt = expiresAt,
        )
    }

    fun renew(
        user: AuthenticatedUser,
    ): SignInDto {
        val (token, expiresAt) = generateToken(
            secret = secret,
            id = user.id,
            username = user.username,
        )
        return SignInDto(
            username = user.username,
            role = user.role,
            token = token,
            createAt = user.createdAt,
            expiresAt = expiresAt,
        )
    }

    suspend fun signUp(
        email: String,
        emailCode: String,
        username: String,
        password: String,
    ): SignInDto {
        if (username.length < 3) throwBadRequest("用户名至少为3个字符")
        if (username.length > 15) throwBadRequest("用户名至多为15个字符")
        if (password.length < 8) throwBadRequest("密码至少为8个字符")
        if (userRepo.getByEmail(email) != null) throwConflict("邮箱已经被使用")
        if (userRepo.getByUsername(username) != null) throwConflict("用户名已经被使用")
        if (!userRepo.validateEmailCode(email, emailCode)) throwBadRequest("邮箱验证码错误")

        val role = User.Role.Normal
        val userId = userRepo.add(
            email = email,
            username = username,
            password = password,
        ).toHexString()

        val (token, expiresAt) = generateToken(
            secret = secret,
            id = userId,
            username = username,
        )

        return SignInDto(
            username = username,
            role = role,
            token = token,
            createAt = Clock.System.now(),
            expiresAt = expiresAt,
        )
    }

    suspend fun sendVerifyEmail(email: String) {
        try {
            InternetAddress(email).apply { validate() }
        } catch (e: AddressException) {
            throwBadRequest("邮箱不合法")
        }

        if (userRepo.getByEmail(email) != null) throwConflict("邮箱已经被使用")

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
            throwInternalServerError("邮件发送失败")
        }

        userRepo.addEmailCode(email, emailCode)
    }

    suspend fun sendResetPasswordTokenEmail(emailOrUsername: String) {
        val user = userRepo.getByUsernameOrEmail(emailOrUsername)
            ?: throwUserNotFound()

        val token = UUID.randomUUID().toString()

        try {
            Email.send(
                to = user.email,
                subject = "日本网文机翻机器人 重置密码口令",
                text = "您的重置密码口令为 $token\n" +
                        "口令将会在15分钟后失效,请尽快重置密码\n" +
                        "如果发送了多个口令，请使用最新的口令，旧的口令将失效\n" +
                        "这是系统邮件，请勿回复"
            )
        } catch (e: AddressException) {
            throwInternalServerError("邮件发送失败")
        }

        userRepo.addResetPasswordToken(user.id, token)
    }

    suspend fun resetPassword(
        emailOrUsername: String,
        token: String,
        password: String,
    ) {
        val user = userRepo.getByUsernameOrEmail(emailOrUsername)
            ?: throwUserNotFound()
        if (!userRepo.validateResetPasswordToken(user.id, token)) {
            throwBadRequest("口令不合法")
        }
        userRepo.updatePassword(user.id, password)
    }
}
