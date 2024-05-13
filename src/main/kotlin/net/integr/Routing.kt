package net.integr

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import net.integr.cookie.UserSession
import net.integr.data.userstorage.ServerUser
import net.integr.data.requests.LoginData
import net.integr.data.requests.SignupData
import net.integr.data.userstorage.UserStorage
import net.integr.data.webdata.User
import net.integr.email.CodeStorage
import net.integr.email.EmailService
import net.integr.email.EmailVerificationPiece
import net.integr.encryption.Encryption

fun Application.configureRouting() {
    routing {
        staticResources("/", "static")
        routeLogin()
        routeSignup()
        routeVerify()

        get("/users") {
            call.respond(UserStorage.users)
        }
    }
}

/**
 * Accepts JSON
 * ```json
 * {
 *  "username": "Erik",
 *  "password": "28d4cd88a9e3c3ed5c075d63ad8ad2a8eb12f589fc607aa6ad11ffe42a28660e56012afaf48d78f387976dc0427cd02f5db285fa707525d2e936ad3eb23fc65f"
 * }
 * ```
 *
 * The password must be an SHA-512 Hash
 */
@KtorDsl fun Route.routeLogin() {
    post("/login") {
        try {
            val loginData = call.receiveNullable<LoginData>()

            if (loginData != null && loginData.username.isNotEmpty() && loginData.password.isNotEmpty()) { // Check if all data is there
                if (Encryption.validateSHA512(loginData.password)) { // Validate the SHA-512 Hash
                    val username = loginData.username
                    val user = UserStorage.getFromUsername(username) // Find the username in the database

                    if (user != null) { // Check if the user exists
                        val enteredPassword = Encryption.hash(loginData.password + user.salt) // Hash the password that was entered
                        val actualPassword = user.passwordHash

                        if (enteredPassword == actualPassword) { // Compare the passwords
                            call.sessions.set(UserSession(user.user)) // Set the cookie
                            call.respond(HttpStatusCode.OK, "Logged in. Cookie set.")
                        } else call.respond(HttpStatusCode.Forbidden, "Invalid credentials.")
                    } else call.respond(HttpStatusCode.BadRequest, "Username does not exist.")
                } else call.respond(HttpStatusCode.BadRequest, "Invalid hash.")
            } else call.respond(HttpStatusCode.BadRequest, "Missing data.")
        } catch (e: NullPointerException) {
            call.respond(HttpStatusCode.BadRequest, "Missing data.")
        }
    }
}

/**
 * Accepts JSON
 * ```json
 * {
 *  "username": "Erik",
 *  "email": "erik@gmail.com",
 *  "password": "28d4cd88a9e3c3ed5c075d63ad8ad2a8eb12f589fc607aa6ad11ffe42a28660e56012afaf48d78f387976dc0427cd02f5db285fa707525d2e936ad3eb23fc65f"
 * }
 * ```
 *
 * The password must be an SHA-512 Hash
 */
@KtorDsl fun Route.routeSignup() {
    post("/signup") {
        try {
            val signupData = call.receiveNullable<SignupData>() // Get the signup json data

            if (signupData != null && signupData.username.isNotEmpty() && signupData.password.isNotEmpty() && signupData.password.isNotEmpty()) {
                val username = signupData.username
                if (UserStorage.getFromUsername(username) == null && CodeStorage.getFromUsername(username) == null) {
                    val email = signupData.email
                    if (UserStorage.getFromEmail(email) == null && CodeStorage.getFromEmail(email) == null) {
                        if (EmailService.verifyEmail(email)) {
                            val enteredPassword = signupData.password

                            val salt = Encryption.generateSalt() // Generate the salt
                            val hashedPassword = Encryption.hash(enteredPassword + salt) // Hash the password

                            val emailVerify = EmailService.generateVerify("https://jhv8mnnb-8080.euw.devtunnels.ms") // Generate the email verification url

                            //EmailService.send(emailVerifyUrl, email) //TODO: Send email instead of printing

                            CodeStorage.awaiting += EmailVerificationPiece(System.currentTimeMillis(), email, emailVerify.second, hashedPassword, salt, username)
                            CodeStorage.save()

                            call.respond(HttpStatusCode.OK, "Verification code sent. TEMP: ${emailVerify.first}")
                        } else call.respond(HttpStatusCode.BadRequest, "Invalid email.")
                    } else call.respond(HttpStatusCode.BadRequest, "Email already exist.")
                } else call.respond(HttpStatusCode.BadRequest, "Username already exist.")
            } else call.respond(HttpStatusCode.BadRequest, "Missing data.")
        } catch (e: NullPointerException) {
            call.respond(HttpStatusCode.BadRequest, "Missing data.")
        }
    }
}

@KtorDsl fun Route.routeVerify() {
    get("/verify") {
        val code = call.parameters["code"]
        if (code != null) {
            try {
                val savedCode = CodeStorage.getByCode(code.toInt())

                if (savedCode != null) {
                    val user = User(UserStorage.generateID(), savedCode.username, savedCode.creation)
                    UserStorage.users += ServerUser(savedCode.username, savedCode.email, savedCode.hashedPass, savedCode.salt, user)
                    CodeStorage.awaiting.remove(savedCode)
                    CodeStorage.save()
                    UserStorage.save()
                    call.respond(HttpStatusCode.OK, "User registered.")
                } else call.respond(HttpStatusCode.BadRequest, "Invalid code.")
            } catch(e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid code.")
            }
        } else call.respond(HttpStatusCode.BadRequest, "Missing code.")
    }
}
