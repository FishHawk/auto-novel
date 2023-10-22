package api.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.days

object RateLimitNames {
    val CreateArticle = RateLimitName("create-article")
    val CreateComment = RateLimitName("create-comment")
    val CreateWenkuNovel = RateLimitName("create-wenku-novel")
    val CreateWenkuVolume = RateLimitName("create-wenku-volume")
}

fun Application.rateLimit() = install(RateLimit) {
    register(RateLimitNames.CreateArticle) {
        rateLimiter(limit = 10, refillPeriod = 1.days)
        requestKey { call -> call.authenticatedUser().id }
    }
    register(RateLimitNames.CreateComment) {
        rateLimiter(limit = 100, refillPeriod = 1.days)
        requestKey { call -> call.authenticatedUser().id }
    }
    register(RateLimitNames.CreateWenkuNovel) {
        rateLimiter(limit = 100, refillPeriod = 1.days)
        requestKey { call -> call.authenticatedUser().id }
    }
    register(RateLimitNames.CreateWenkuVolume) {
        rateLimiter(limit = 100, refillPeriod = 1.days)
        requestKey { call -> call.authenticatedUser().id }
    }
}
