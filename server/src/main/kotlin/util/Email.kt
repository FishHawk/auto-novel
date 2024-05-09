package util

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

object Email {
    suspend fun send(to: String, subject: String, text: String) {
        withContext(Dispatchers.IO) {
            val props = Properties()
            props["mail.smtp.auth"] = "true"
            props["mail.smtp.ssl.enable"] = "true"
            props["mail.smtp.starttls.enable"] = "true"
            props["mail.smtp.host"] = "smtp.qiye.aliyun.com"
            props["mail.smtp.port"] = "465"

            val authenticator = object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    val username = "no-reply@notify.fishhawk.top"
                    val password = System.getenv("EMAIL_PASSWORD")!!
                    return PasswordAuthentication(username, password)
                }
            }

            val session = Session.getInstance(props, authenticator)

            val message = MimeMessage(session)
            val from = "no-reply@notify.fishhawk.top"
            message.setFrom(InternetAddress(from))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.subject = subject
            message.setText(text)

            Transport.send(message)
        }
    }
}
