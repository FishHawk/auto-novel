package infra

import infra.web.providers.Alphapolis
import infra.web.providers.Hameln
import infra.web.providers.Pixiv
import infra.web.providers.Syosetu
import io.kotest.mpp.env
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

val client = HttpClient(Java) {
    install(HttpCookies) {
        default {
            Alphapolis.addCookies(this)
            Hameln.addCookies(this)
            Pixiv.addCookies(this, phpsessid = env("PIXIV_COOKIE_PHPSESSID"))
            Syosetu.addCookies(this)
        }
    }
    install(ContentNegotiation) {
        json(Json {
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    expectSuccess = true
    engine {
        proxy = ProxyBuilder.http("http://127.0.0.1:7890")
    }
}
