package net.integr

import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import io.ktor.server.tomcat.*
import io.ktor.util.*
import kotlinx.html.*
import net.integr.cookie.UserSession
import net.integr.data.userstorage.UserStorage
import net.integr.email.CodeStorage
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Tomcat, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

val codeRemoverThread = Thread {
    while(true) {
        val curr = System.currentTimeMillis()

        for (c in CodeStorage.awaiting) {
            if (curr - c.creation > 1000 * 60) { // 1 Minute
                CodeStorage.awaiting.remove(c)
                CodeStorage.save()
            }
        }
    }
}

fun Application.module() {
    codeRemoverThread.start()

    UserStorage.load()
    CodeStorage.load()

    install(Sessions) {
        val secretSignKey = hex("6819b47a326945c1968f45236389")
        val secretEncryptKey = hex("00112243445566778899aabbccdeeeff")

        cookie<UserSession>("session", SessionStorageMemory()) {
            cookie.secure = true
            cookie.path = "/"
            cookie.maxAgeInSeconds = 60*60 // 60 Minutes
            transform(SessionTransportTransformerEncrypt(secretEncryptKey, secretSignKey))
        }
    }

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondStatusPage(text = "$cause", status = HttpStatusCode.InternalServerError)
        }

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondStatusPage(text = "Too many requests. Wait for $retryAfter seconds.", status = status)
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respondStatusPage(text = "Not Found", status = status)
        }
    }

    install(RateLimit) {
        register(RateLimitName("signup")) {
            rateLimiter(limit = 1, refillPeriod = 120.seconds)
        }
    }

    configureRouting()
}

suspend fun ApplicationCall.respondStatusPage(text: String, status: HttpStatusCode = HttpStatusCode.OK) {
    this.respondHtml(status = status) {
        head {
            styleLink("/status_page_style.css")

            script {
                src = "https://kit.fontawesome.com/0a7e2ccef9.js"
            }
        }

        body {
            div(classes = "title_container") {
                h1(classes = "left") {
                    +"Queue Underflow"
                }
            }

            div(classes = "top_container") {
                h1(classes = "center") {
                    i("fa-solid fa-xmark")

                    +" ${status.value} - $text"
                }
            }
        }
    }
}
