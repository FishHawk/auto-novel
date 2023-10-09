package infra

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient

typealias DataSourceRedis = KredsClient

fun createRedisDataSource(url: String): DataSourceRedis {
    return newClient(Endpoint.from(url))
}