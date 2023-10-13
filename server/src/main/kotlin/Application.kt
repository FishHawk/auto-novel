import api.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import infra.*
import infra.DataSourceWebNovelProvider
import infra.common.*
import infra.web.*
import infra.wenku.WenkuNovelMetadataRepository
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
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
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
            routeOperationHistory()
            routeWebNovel()
            routeWenkuNovel()
        }
    }.start(wait = true)
}

val appModule = module {
    // Data Source
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
    single {
        val httpProxy: String? = System.getenv("HTTPS_PROXY")
        val pixivPhpsessid: String? = System.getenv("PIXIV_COOKIE_PHPSESSID")
        DataSourceWebNovelProvider(
            httpsProxy = httpProxy,
            pixivPhpsessid = pixivPhpsessid,
        )
    }

    // Repository
    singleOf(::ArticleRepository)
    singleOf(::CommentRepository)
    singleOf(::OperationHistoryRepository)
    singleOf(::UserRepository)

    singleOf(::WebNovelMetadataRepository)
    singleOf(::WebNovelChapterRepository)
    singleOf(::WebNovelFileRepository)

    singleOf(::WenkuNovelMetadataRepository)
    singleOf(::WenkuNovelVolumeRepository)

    // Api
    single {
        val secret = System.getenv("JWT_SECRET")!!
        AuthApi(secret, get())
    } withOptions {
        createdAtStart()
    }
    singleOf(::ArticleApi) { createdAtStart() }
    singleOf(::CommentApi) { createdAtStart() }
    singleOf(::OperationHistoryApi) { createdAtStart() }
    singleOf(::WebNovelApi) { createdAtStart() }
    singleOf(::WenkuNovelApi) { createdAtStart() }
}
