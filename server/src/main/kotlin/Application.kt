import api.*
import api.plugins.authentication
import api.plugins.contentNegotiation
import api.plugins.rateLimit
import infra.DataSourceElasticSearch
import infra.DataSourceFileSystem
import infra.DataSourceMongo
import infra.common.ArticleRepository
import infra.common.CommentRepository
import infra.common.OperationHistoryRepository
import infra.common.SakuraJobRepository
import infra.createRedisDataSource
import infra.user.UserFavoredWebRepository
import infra.user.UserFavoredWenkuRepository
import infra.user.UserReadHistoryWebRepository
import infra.user.UserRepository
import infra.web.DataSourceWebNovelProvider
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelFileRepository
import infra.web.WebNovelMetadataRepository
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import sakura.SakuraWorkerManager

fun main() {
    embeddedServer(Netty, 8081) {
        install(Koin) {
            slf4jLogger()
            modules(appModule)
        }
        authentication(secret = System.getenv("JWT_SECRET")!!)
        rateLimit()
        install(Resources)
        install(CachingHeaders)
        contentNegotiation()
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
            //
            routeSakura()
            //
            routeUser()
            routeUserFavoredWeb()
            routeUserFavoredWenku()
            routeUserReadHistoryWeb()
            //
            routeWebNovel()
            routeWenkuNovel()
        }
    }.start(wait = true)
}

val appModule = module {
    fun env(name: String): String? =
        System.getenv(name)

    fun envDbHost(name: String) =
        env(name)
            ?: env("DB_HOST_TEST")
            ?: "localhost"

    fun envDbPort(name: String) =
        env(name)
            ?.toIntOrNull()

    // Data Source
    single {
        DataSourceMongo(
            host = envDbHost("DB_HOST_MONGO"),
            port = envDbPort("DB_PORT_MONGO"),
        )
    }
    single {
        DataSourceElasticSearch(
            host = envDbHost("DB_HOST_ES"),
            port = envDbPort("DB_PORT_ES"),
        )
    }
    single {
        createRedisDataSource(
            host = envDbHost("DB_HOST_REDIS"),
            port = envDbPort("DB_PORT_REDIS"),
        )
    }
    single {
        DataSourceWebNovelProvider(
            httpsProxy = env("HTTPS_PROXY"),
            pixivPhpsessid = env("PIXIV_COOKIE_PHPSESSID"),
        )
    }
    singleOf(::DataSourceFileSystem)

    // Repository
    singleOf(::ArticleRepository)
    singleOf(::CommentRepository)
    singleOf(::OperationHistoryRepository)

    singleOf(::SakuraWorkerManager)
    singleOf(::SakuraJobRepository)

    singleOf(::UserRepository)
    singleOf(::UserFavoredWebRepository)
    singleOf(::UserFavoredWenkuRepository)
    singleOf(::UserReadHistoryWebRepository)

    singleOf(::WebNovelMetadataRepository)
    singleOf(::WebNovelChapterRepository)
    singleOf(::WebNovelFileRepository)

    singleOf(::WenkuNovelMetadataRepository)
    singleOf(::WenkuNovelVolumeRepository)

    // Api
    single {
        AuthApi(
            secret = env("JWT_SECRET")!!,
            get(),
        )
    } withOptions { createdAtStart() }
    singleOf(::ArticleApi) { createdAtStart() }
    singleOf(::CommentApi) { createdAtStart() }
    singleOf(::OperationHistoryApi) { createdAtStart() }

    singleOf(::SakuraApi) { createdAtStart() }

    singleOf(::UserApi) { createdAtStart() }
    singleOf(::UserFavoredWebApi) { createdAtStart() }
    singleOf(::UserFavoredWenkuApi) { createdAtStart() }
    singleOf(::UserReadHistoryWebApi) { createdAtStart() }

    singleOf(::WebNovelApi) { createdAtStart() }
    singleOf(::WenkuNovelApi) { createdAtStart() }
}
