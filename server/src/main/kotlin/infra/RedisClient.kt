package infra

import io.github.crackthecodeabhi.kreds.args.SetOption
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlin.time.Duration.Companion.hours

typealias RedisClient = KredsClient

fun redisClient(host: String, port: Int?): RedisClient {
    return newClient(Endpoint(host, port ?: 6379))
}

suspend inline fun RedisClient.withRateLimit(key: String, block: () -> Unit) {
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
