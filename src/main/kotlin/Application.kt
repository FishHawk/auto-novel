import api.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.*
import data.ElasticSearchDataSource
import data.web.EsBookMetadataRepository
import data.web.WebBookFileRepository
import data.wenku.WenkuBookFileRepository
import data.provider.ProviderDataSource
import data.web.BookEpisodeRepository
import data.web.BookMetadataRepository
import data.web.BookPatchRepository
import data.wenku.WenkuBookMetadataRepository
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
import kotlin.io.path.Path

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
            routePrepareBook()
            routeWenkuNovel()
            routeWebNovel()
            routePatch()
            routeUpdate()
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
    single { ProviderDataSource() }

    single { WenkuBookFileRepository(root = Path("./data/files-wenku")) }
    single { WenkuBookMetadataRepository(get()) }

    single { BookMetadataRepository(get(), get(), get()) }
    single { BookEpisodeRepository(get(), get(), get()) }
    single { BookPatchRepository(get(), get(), get()) }
    single { WebBookFileRepository(root = Path("./data/files-web")) }
    single { EsBookMetadataRepository(get()) }

    single { CommentRepository(get()) }
    single { UserRepository(get()) }
    single { EmailCodeRepository(get()) }

    single {
        val secret = System.getenv("JWT_SECRET")!!
        AuthService(secret, get(), get())
    }
    single { CommentService(get()) }

    single { PrepareBookService(get(), get(), get()) }
    single { WebNovelService(get(), get(), get(), get(), get()) }
    single { PatchService(get()) }
    single { UpdateService(get(), get()) }

    single { WenkuNovelService(get(), get()) }
}
