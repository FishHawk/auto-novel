package infra.user

import infra.RedisClient
import io.github.crackthecodeabhi.kreds.args.SetOption
import kotlin.time.Duration.Companion.minutes

class UserCodeRepository(
    private val redis: RedisClient,
) {
    private fun emailCodeKey(email: String) = "ec:${email}"

    suspend fun verifyEmailCode(email: String, emailCode: String): Boolean {
        val inRedis = redis.get(key = emailCodeKey(email))
        return inRedis == emailCode
    }

    suspend fun addEmailCode(email: String, emailCode: String) {
        redis.set(
            key = emailCodeKey(email),
            value = emailCode,
            setOption = SetOption.Builder()
                .exSeconds(15.minutes.inWholeSeconds.toULong())
                .build(),
        )
    }

    private fun resetPasswordCodeKey(id: String) = "rpt:${id}"

    suspend fun verifyResetPasswordToken(id: String, resetCode: String): Boolean {
        val inRedis = redis.get(key = resetPasswordCodeKey(id))
        return inRedis == resetCode
    }

    suspend fun addResetPasswordCode(id: String, resetCode: String) {
        redis.set(
            key = resetPasswordCodeKey(id),
            value = resetCode,
            setOption = SetOption.Builder()
                .exSeconds(15.minutes.inWholeSeconds.toULong())
                .build(),
        )
    }
}