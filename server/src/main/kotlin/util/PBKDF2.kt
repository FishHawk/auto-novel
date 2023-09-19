package util

import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PBKDF2 {
    fun randomSalt(): String {
        return UUID.randomUUID().toString()
    }

    fun hash(password: String, salt: String): String {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 120_000, 256)
        val hashed = factory.generateSecret(spec).encoded
        return String(Base64.getEncoder().encode(hashed))
    }
}
