package net.integr

import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.tomcat.*
import io.ktor.util.*
import net.integr.config.ConfigStorage
import net.integr.cookie.UserSession
import net.integr.data.tickets.TicketStorage
import net.integr.data.users.UserStorage
import net.integr.email.CodeStorage
import net.integr.email.EmailVerificationPiece
import kotlin.time.Duration.Companion.seconds

//TODO: Profile Icons
fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Tomcat, port = port, host = "localhost", module = Application::module)
        .start(wait = true)
}

val codeRemoverThread = Thread {
    while (true) {
        val curr = System.currentTimeMillis()

        val newL: MutableList<EmailVerificationPiece> = mutableListOf()
        newL.addAll(CodeStorage.awaiting)

        for (c in CodeStorage.awaiting) {
            if (curr - c.creation > 1000 * 60) { // 1 Minute
                newL.remove(c)
            }
        }

        if (!newL.containsAll(CodeStorage.awaiting)) {
            CodeStorage.awaiting = newL
        }

        Thread.sleep(2000)
    }
}

fun Application.module() {
    UserStorage.load()
    TicketStorage.load()
    ConfigStorage.load()

    codeRemoverThread.start()

    install(Sessions) {
        val secretSignKey = hex(ConfigStorage.INSTANCE!!.sessionSignKey)
        val secretEncryptKey = hex(ConfigStorage.INSTANCE!!.sessionEncryptKey)

        cookie<UserSession>("session", SessionStorageMemory()) {
            cookie.secure = false
            cookie.path = "/"
            cookie.maxAgeInSeconds = 60*60 // 60 Minutes
            transform(SessionTransportTransformerEncrypt(secretEncryptKey, secretSignKey))
            cookie.httpOnly = true
        }
    }

    install(ContentNegotiation) {
        gson {}
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondStatusPage(text = "Internal server error", status = HttpStatusCode.InternalServerError)
            cause.printStackTrace()
        }

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respondStatusPage(text = "Too many requests. Wait for $retryAfter seconds.", status = status)
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respondStatusPage(text = "Not found", status = status)
        }
    }

    install(RateLimit) {
        register(RateLimitName("signup")) {
            rateLimiter(limit = 1, refillPeriod = 120.seconds)
        }

        register(RateLimitName("post")) {
            rateLimiter(limit = 2, refillPeriod = 120.seconds)
        }

        register(RateLimitName("comment")) {
            rateLimiter(limit = 5, refillPeriod = 120.seconds)
        }

        register(RateLimitName("delete")) {
            rateLimiter(limit = 1, refillPeriod = 120.seconds)
        }
    }

    install(CORS) {
        anyHost()
        //allowHost("localhost:5173", listOf("http"), listOf("/"))
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.SetCookie)
        allowHeader(HttpHeaders.Cookie)
        allowCredentials = true
    }

    configureRouting()
}

suspend fun ApplicationCall.respondStatusPage(text: String, status: HttpStatusCode = HttpStatusCode.OK) {
    this.respondText("${status.value}: $text", status = status)
}
