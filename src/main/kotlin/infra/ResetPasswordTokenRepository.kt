package infra

import com.mongodb.client.model.UpdateOptions
import kotlinx.datetime.Clock
import org.bson.types.ObjectId
import org.litote.kmongo.eq
import org.litote.kmongo.id.toId

class ResetPasswordTokenRepository(
    private val mongo: MongoDataSource,
) {
    suspend fun set(userId: ObjectId, token: String) {
        mongo
            .resetPasswordTokenCollection
            .updateOne(
                ResetPasswordToken::userId eq userId.toId(),
                ResetPasswordToken(
                    userId = userId.toId(),
                    token = token,
                    createAt = Clock.System.now(),
                ),
                UpdateOptions().upsert(true),
            )
    }

    suspend fun validate(userId: ObjectId, token: String): Boolean {
        return mongo
            .resetPasswordTokenCollection
            .findOne(
                ResetPasswordToken::userId eq userId.toId(),
                ResetPasswordToken::token eq token,
            ) != null
    }
}
