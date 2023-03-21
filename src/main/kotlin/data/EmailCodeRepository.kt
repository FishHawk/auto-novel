package data

import com.mongodb.client.model.IndexOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.litote.kmongo.eq
import java.util.*
import java.util.concurrent.TimeUnit

@Serializable
private data class EmailCode(
    val email: String,
    val emailCode: String,
    @Contextual val createdAt: Date,
)

class EmailCodeRepository(
    private val mongoDataSource: MongoDataSource,
) {
    private val col
        get() = mongoDataSource.database.getCollection<EmailCode>("email-code")

    init {
        runBlocking {
            col.ensureIndex(
                EmailCode::createdAt,
                indexOptions = IndexOptions().expireAfter(5, TimeUnit.MINUTES),
            )
        }
    }

    suspend fun exist(email: String, emailCode: String): Boolean {
        return col.findOne(
            EmailCode::email eq email,
            EmailCode::emailCode eq emailCode,
        ) != null
    }

    suspend fun add(email: String, emailCode: String) {
        col.insertOne(
            EmailCode(
                email = email,
                emailCode = emailCode,
                createdAt = Date(),
            )
        )
    }
}