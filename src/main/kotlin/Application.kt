import api.routePrepareBook
import api.routeNovel
import api.routeUpdateJp
import api.routeUpdateZh
import data.BookRepository
import data.createDatabase
import io.ktor.server.engine.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    val database = createDatabase()
    val bookRepo = BookRepository(database)

    // Start server.
    embeddedServer(Netty, 8081) {
        install(Resources)
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

        routing {
            routePrepareBook(bookRepo)
            routeNovel(bookRepo)
            routeUpdateJp(bookRepo)
            routeUpdateZh(bookRepo)
        }
    }.start(wait = true)
}