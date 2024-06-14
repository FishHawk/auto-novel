package infra.common

import infra.DataSourceMongo
import domain.entity.SakuraWebIncorrectCase

class SakuraFeedbackRepository(
    private val mongo: DataSourceMongo,
) {
    suspend fun createWebIncorrectCase(
        case: SakuraWebIncorrectCase,
    ) {
        mongo
            .sakuraWebIncorrectCaseCollection
            .insertOne(case)
    }
}