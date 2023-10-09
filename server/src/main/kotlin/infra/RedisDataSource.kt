package infra

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient

typealias RedisDataSource = KredsClient

fun createRedisDataSource(url: String): RedisDataSource {
    return newClient(Endpoint.from(url))
}