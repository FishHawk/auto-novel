package infra

import org.litote.kmongo.eq
import java.util.*

class EmailCodeRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun exist(email: String, emailCode: String): Boolean {
        return mongo
            .emailCodeCollection
            .findOne(
                EmailCodeModel::email eq email,
                EmailCodeModel::emailCode eq emailCode,
            ) != null
    }

    suspend fun add(email: String, emailCode: String) {
        mongo
            .emailCodeCollection
            .insertOne(
                EmailCodeModel(
                    email = email,
                    emailCode = emailCode,
                    createdAt = Date(),
                )
            )
    }
}