package infra.user

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import infra.MongoClient
import infra.MongoCollectionNames
import infra.field
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId

class UserFavoredRepository(
    mongo: MongoClient,
) {
    private val userCollection =
        mongo.database.getCollection<UserDbModel>(
            MongoCollectionNames.USER,
        )

    suspend fun getFavoredList(
        id: String,
    ): UserFavoredList? {
        return userCollection
            .withDocumentClass<UserFavoredList>()
            .find(eq(UserDbModel::id.field(), ObjectId(id)))
            .firstOrNull()
    }

    suspend fun updateFavoredWeb(
        userId: String,
        favored: List<UserFavored>,
    ) {
        userCollection
            .updateOne(
                eq(UserDbModel::id.field(), ObjectId(userId)),
                set(UserDbModel::favoredWeb.field(), favored)
            )
    }

    suspend fun updateFavoredWenku(
        userId: String,
        favored: List<UserFavored>,
    ) {
        userCollection
            .updateOne(
                eq(UserDbModel::id.field(), ObjectId(userId)),
                set(UserDbModel::favoredWenku.field(), favored),
            )
    }
}