import api.*
import api.routeUpdateJp
import data.BookEpisodeRepository
import data.BookMetadataRepository
import data.BookPatchRepository
import data.MongoDataSource
import data.file.BookFileRepository
import data.provider.ProviderDataSource
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
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
        install(Resources)
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
                call.application.environment.log.error("未捕获异常:", cause)
                call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            }
        }

        routing {
            routePrepareBook()
            routeNovel()
            routePatch()
            routeUpdateJp()
            routeUpdateZh()
        }
    }.start(wait = true)
}

val appModule = module {
    single { MongoDataSource(System.getenv("MONGODB_URL") ?: "mongodb://localhost:27017") }
    single { ProviderDataSource() }

    single { BookMetadataRepository(get(), get()) }
    single { BookEpisodeRepository(get(), get(), get()) }
    single { BookPatchRepository(get(), get(), get()) }
    single { BookFileRepository() }

    single { PrepareBookService(get(), get(), get()) }
    single { NovelService(get(), get()) }
    single { PatchService(get()) }
    single { UpdateJpService(get(), get()) }
    single { UpdateZhService(get(), get()) }
}
