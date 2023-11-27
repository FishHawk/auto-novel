import api.*
import api.plugins.authentication
import api.plugins.contentNegotiation
import api.plugins.rateLimit
import infra.DataSourceElasticSearch
import infra.DataSourceFileSystem
import infra.DataSourceMongo
import infra.common.*
import infra.createRedisDataSource
import infra.web.DataSourceWebNovelProvider
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelFileRepository
import infra.web.WebNovelMetadataRepository
import infra.personal.PersonalNovelVolumeRepository
import infra.user.UserFavoredWebRepository
import infra.user.UserFavoredWenkuRepository
import infra.user.UserReadHistoryWebRepository
import infra.user.UserRepository
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
            routePersonalNovel()
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
    singleOf(::PersonalNovelVolumeRepository)

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

    singleOf(::SakuraApi) { createdAtStart() }

    singleOf(::UserApi) { createdAtStart() }
    singleOf(::UserFavoredWebApi) { createdAtStart() }
    singleOf(::UserFavoredWenkuApi) { createdAtStart() }
    singleOf(::UserReadHistoryWebApi) { createdAtStart() }

    singleOf(::WebNovelApi) { createdAtStart() }
    singleOf(::WenkuNovelApi) { createdAtStart() }
    singleOf(::PersonalNovelApi) { createdAtStart() }
}
