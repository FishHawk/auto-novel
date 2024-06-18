package infra.sakura

import infra.MongoClient
import infra.MongoCollectionNames

class SakuraFeedbackRepository(
    mongo: MongoClient,
) {
    private val sakuraWebIncorrectCaseCollection =
        mongo.database.getCollection<SakuraWebIncorrectCase>(
            MongoCollectionNames.SAKURA_WEB_INCORRECT_CASE,
        )

    suspend fun createWebIncorrectCase(
        case: SakuraWebIncorrectCase,
    ) {
        sakuraWebIncorrectCaseCollection
            .insertOne(case)
    }
}