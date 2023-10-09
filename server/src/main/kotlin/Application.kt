import api.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import infra.*
import infra.DataSourceWebNovelProvider
import infra.common.ArticleRepository
import infra.common.CommentRepository
import infra.common.StatisticsRepository
import infra.common.UserRepository
import infra.web.*
import infra.wenku.WenkuNovelEditHistoryRepository
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelUploadHistoryRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, 8081) {
        install(Koin) {
            slf4jLogger()
            modules(appModule)
        }
        install(Authentication) {
            jwt {
                val secret = System.getenv("JWT_SECRET")!!
                verifier(
                    JWT.require(Algorithm.HMAC256(secret)).build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("username").asString() != "") {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
                challenge { defaultScheme, realm ->
                    call.respond(HttpStatusCode.Unauthorized, "Token不合法或者过期")
                }
            }
        }
        install(Resources)
        install(CachingHeaders)
        install(ContentNegotiation) {
            json(Json)
        }
        install(CallLogging) {
            format { call ->
                val status = call.response.status()
                val httpMethod = call.request.httpMethod.value
                val uri = call.request.uri
                "$httpMethod-$status $uri"
            }
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                val httpMethod = call.request.httpMethod.value
                val uri = call.request.uri
                call.application.environment.log.error("未捕获异常 $httpMethod-$uri:", cause)
                call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            }
        }

        routing {
            routeAuth()
            routeArticle()
            routeComment()

            routeWebNovel()
            routeWebNovelAdmin()

            routeWenkuNovel()
            routeWenkuNovelAdmin()
        }
    }.start(wait = true)
}

val appModule = module {
    single {
        val mongodbUrl = System.getenv("MONGODB_URL") ?: "mongodb://192.168.1.110:27017"
        DataSourceMongo(mongodbUrl)
    }
    single {
        val url = System.getenv("ELASTIC_SEARCH_DB_URL") ?: "192.168.1.110"
        DataSourceElasticSearch(url)
    }
    single {
        val url = System.getenv("REDIS_URL") ?: "192.168.1.110:6379"
        createRedisDataSource(url)
    }
    single { DataSourceWebNovelProvider() }

    // Repository
    single { WebNovelMetadataRepository(get(), get(), get()) }
    single { WebNovelChapterRepository(get(), get()) }
    single { WebNovelFileRepository(get()) }
    single { WebNovelPatchHistoryRepository(get()) }
    single { WebNovelTocMergeHistoryRepository(get()) }

    single { WenkuNovelMetadataRepository(get(), get()) }
    single { WenkuNovelVolumeRepository() }
    single { WenkuNovelUploadHistoryRepository(get()) }
    single { WenkuNovelEditHistoryRepository(get()) }

    single { ArticleRepository(get()) }
    single { CommentRepository(get()) }
    single { StatisticsRepository(get(), get(), get()) }
    single { UserRepository(get(), get(), get()) }

    // Api
    single(createdAtStart = true) {
        val secret = System.getenv("JWT_SECRET") ?: ""
        AuthApi(secret, get())
    }
    single(createdAtStart = true) { ArticleApi(get(), get()) }
    single(createdAtStart = true) { CommentApi(get(), get(), get()) }

    single(createdAtStart = true) { WebNovelApi(get(), get(), get(), get(), get(), get(), get()) }
    single(createdAtStart = true) { WebNovelAdminApi(get(), get(), get()) }

    single(createdAtStart = true) { WenkuNovelApi(get(), get(), get(), get(), get(), get()) }
    single(createdAtStart = true) { WenkuNovelAdminApi(get(), get()) }
}
