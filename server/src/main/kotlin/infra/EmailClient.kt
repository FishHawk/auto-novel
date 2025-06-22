package infra

import infra.web.datasource.providers.json
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class EmailClient(private val apiKey: String?) {
    private val client = HttpClient(Java) {
        install(ContentNegotiation) {
            json(Json { isLenient = true })
        }
        expectSuccess = true
    }

    val enabled
        get() = apiKey != null

    private suspend fun send(to: String, subject: String, text: String) {
        if (apiKey == null) return

        client.post(apiEndpoint) {
            basicAuth("api", apiKey)
            url {
                parameters.append("from", "轻小说机翻机器人 <${postfrom}>")
                parameters.append("to", to)
                parameters.append("subject", subject)
                parameters.append("text", text)
            }
        }.json()
    }

    suspend fun sendVerifyEmail(to: String, code: String) =
        send(
            to = to,
            subject = "$code 日本网文机翻机器人 注册激活码",
            text = "您的注册激活码为 $code\n" +
                    "激活码将会在15分钟后失效,请尽快完成注册\n" +
                    "这是系统邮件，请勿回复"
        )

    suspend fun sendResetPasswordTokenEmail(to: String, token: String) =
        send(
            to = to,
            subject = "日本网文机翻机器人 重置密码口令",
            text = "您的重置密码口令为 $token\n" +
                    "口令将会在15分钟后失效,请尽快重置密码\n" +
                    "如果发送了多个口令，请使用最新的口令，旧的口令将失效\n" +
                    "这是系统邮件，请勿回复"
        )
}
