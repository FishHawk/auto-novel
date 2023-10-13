package infra

import io.github.crackthecodeabhi.kreds.args.SetOption
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlin.time.Duration.Companion.hours

typealias DataSourceRedis = KredsClient

fun createRedisDataSource(url: String): DataSourceRedis {
    return newClient(Endpoint.from(url))
}

suspend inline fun DataSourceRedis.withRateLimit(key: String, block: () -> Unit) {
    val keyWithNamespace = "rl:${key}"
    if (exists(keyWithNamespace) == 0L) {
        set(
            key = keyWithNamespace,
            value = "0",
            setOption = SetOption.Builder()
                .exSeconds(3.hours.inWholeSeconds.toULong())
                .build(),
        )
        block()
    }
}
