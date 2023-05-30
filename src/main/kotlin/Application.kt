import api.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import domain.WebNovelUpdateService
import infra.*
import infra.ElasticSearchDataSource
import infra.MongoDataSource
import infra.provider.WebNovelProviderDataSource
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.serializersModuleOf
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: LocalDateTime) =
        encoder.encodeLong(value.atZone(ZoneId.systemDefault()).toEpochSecond())

    override fun deserialize(decoder: Decoder): LocalDateTime =
        Instant.ofEpochSecond(decoder.decodeLong()).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

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
            json(Json {
                serializersModule = serializersModuleOf(LocalDateTimeSerializer)
            })
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
            routeComment()
            routeUser()

            routeWebNovel()
            routeWebNovelAdmin()

            routeWenkuNovel()
        }
    }.start(wait = true)
}

val appModule = module {
    single {
        val mongodbUrl = System.getenv("MONGODB_URL") ?: "mongodb://192.168.1.110:27017"
        MongoDataSource(mongodbUrl)
    }
    single {
        val url = System.getenv("ELASTIC_SEARCH_DB_URL") ?: "192.168.1.110"
        ElasticSearchDataSource(url)
    }
    single { WebNovelProviderDataSource() }

    // Repository
    single { WebNovelMetadataRepository(get(), get(), get()) }
    single { WebNovelChapterRepository(get(), get()) }
    single { WebNovelFileRepository(get()) }
    single { WebNovelPatchHistoryRepository(get()) }
    single { WebNovelTocMergeHistoryRepository(get()) }

    single { WenkuNovelMetadataRepository(get(), get()) }
    single { WenkuNovelVolumeRepository() }

    single { CommentRepository(get()) }
    single { UserRepository(get()) }
    single { EmailCodeRepository(get()) }

    // Service
    single { WebNovelUpdateService(get(), get(), get()) }

    // Api
    single(createdAtStart = true) {
        val secret = System.getenv("JWT_SECRET") ?: ""
        AuthApi(secret, get(), get())
    }
    single(createdAtStart = true) { CommentApi(get()) }
    single(createdAtStart = true) { UserApi(get(), get(), get(), get()) }

    single(createdAtStart = true) { WebNovelApi(get(), get(), get(), get(), get(), get(), get()) }
    single(createdAtStart = true) { WebNovelAdminApi(get(), get(), get()) }

    single(createdAtStart = true) { WenkuNovelApi(get(), get(), get()) }
}
