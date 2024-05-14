package net.integr

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import net.integr.config.ConfigStorage
import net.integr.cookie.UserSession
import net.integr.data.requests.*
import net.integr.data.users.ServerUser
import net.integr.data.tickets.TicketStorage
import net.integr.data.users.UserStorage
import net.integr.data.tickets.extras.Status
import net.integr.data.tickets.Ticket
import net.integr.data.tickets.comment.Comment
import net.integr.data.users.User
import net.integr.email.CodeStorage
import net.integr.email.EmailService
import net.integr.email.EmailVerificationPiece
import net.integr.encryption.Encryption
import kotlin.math.max

fun Application.configureRouting() {
    routing {
        staticResources("/", "static")

        routeSignup()
        routeVerify()

        routeLogin()
        routeLogout()
        routeDeleteAccount()

        routePost()
        routeComment()
        routeDelete()

        routeTickets()
        routeTicketId()

        routeUsers()
        routeCurrentUser()
    }
}

@KtorDsl
fun Route.routeLogin() {
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
                            call.sessions.set(UserSession(user.user.id)) // Set the cookie
                            call.respond(HttpStatusCode.OK, "Logged in. Cookie set.")
                        } else call.respond(HttpStatusCode.BadRequest, "Invalid credentials.")
                    } else call.respond(HttpStatusCode.BadRequest, "Username does not exist.")
                } else call.respond(HttpStatusCode.BadRequest, "Invalid hash.")
            } else call.respond(HttpStatusCode.BadRequest, "Missing data.")
        } catch (e: NullPointerException) {
            call.respond(HttpStatusCode.BadRequest, "Missing data.")
        }
    }
}

@KtorDsl
fun Route.routeSignup() {
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

                            val emailVerify = EmailService.generateVerify(ConfigStorage.INSTANCE!!.mainUrl) // Generate the email verification url

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

@KtorDsl
fun Route.routeVerify() {
    get("/verify") {
        val code = call.parameters["code"]
        if (code != null) {
            try {
                val savedCode = CodeStorage.getByCode(code.toInt())

                if (savedCode != null) {
                    val user = User(UserStorage.generateID(), savedCode.username, savedCode.username, savedCode.creation)
                    UserStorage.users += ServerUser(savedCode.email, savedCode.hashedPass, savedCode.salt, user, false)
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

@KtorDsl
fun Route.routeLogout() {
    post("/logout") {
        val account = call.sessions.get<UserSession>()

        if (account != null) {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK, "Logged Out.")
        } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
    }
}

@KtorDsl
fun Route.routeDeleteAccount() {
    delete("/delete_account") {
        val account = call.sessions.get<UserSession>()

        if (account != null) {
            call.sessions.clear<UserSession>()
            UserStorage.users.remove(UserStorage.getById(account.activeUID))
            UserStorage.save()
            call.respond(HttpStatusCode.OK, "Deleted.")
        } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
    }
}

@KtorDsl
fun Route.routeTickets() {
    get("/tickets") {
        val limit = call.parameters["limit"]
        val offset = call.parameters["offset"]
        val noCom = call.parameters.contains("nocom")

        if (limit != null) {
            try {
                val setLimit = limit.toInt()
                if (setLimit > 0) {
                    val tickets: MutableList<Ticket> = mutableListOf()
                    var os = 0
                    try {
                        if (offset != null) os = max(0, offset.toInt())
                        if (noCom) {
                            TicketStorage.getAmount(setLimit, os).forEach {
                                val t = it.displayNoComCopy()
                                tickets += t
                            }
                        } else tickets.addAll(TicketStorage.getAmount(setLimit, os))
                        call.respond(tickets)
                    } catch (e: NumberFormatException) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid offset.")
                    }
                } else call.respond(HttpStatusCode.BadRequest, "Invalid limit.")
            } catch(e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid limit.")
            }
        } else call.respond(TicketStorage.getAmount(10, 0))
    }
}

@KtorDsl
fun Route.routeTicketId() {
    get("/tickets/{id}") {
        val id = call.parameters["id"]

        if (id != null) {
            try {
                val setId = id.toLong()
                val ticket = TicketStorage.getById(setId)
                if (ticket != null) {
                    call.respond(ticket)
                } else call.respond(HttpStatusCode.BadRequest, "Ticket was not found.")
            } catch (e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id.")
            }
        } else call.respond(HttpStatusCode.BadRequest, "Invalid id.")
    }
}

@KtorDsl
fun Route.routeUsers() {
    get("/users/{id}") {
        val id = call.parameters["id"]

        if (id != null) {
            try {
                val setId = id.toLong()
                val user = UserStorage.getById(setId)
                if (user != null) {
                    call.respond(user.user)
                } else call.respond(HttpStatusCode.BadRequest, "User was not found.")
            } catch (e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id.")
            }
        } else call.respond(HttpStatusCode.BadRequest, "Invalid id.")
    }
}

@KtorDsl
fun Route.routePost() {
    post("/post") {
        val user = call.sessions.get<UserSession>()
        val creationData = call.receiveNullable<CreateData>()

        if (creationData != null && creationData.title.isNotEmpty() && creationData.body.isNotEmpty()) {
            if (user != null) {
                val ticket = Ticket(TicketStorage.generateID(), creationData.title, creationData.body, UserStorage.getById(user.activeUID)!!.user, mutableListOf(), 0, creationData.tags, Status.unsolved, System.currentTimeMillis())
                TicketStorage.tickets += ticket
                TicketStorage.save()
                call.respond(HttpStatusCode.OK, "Ticket created.")
            } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
        } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
    }
}

@KtorDsl
fun Route.routeComment() {
    post("/comment") {
        val user = call.sessions.get<UserSession>()
        val commentData = call.receiveNullable<CommentData>()

        if (commentData != null && commentData.body.isNotEmpty()) {
            if (user != null) {
                val searchedItem = TicketStorage.getGlobalByID(commentData.id)
                if (searchedItem is Ticket) {
                    if (searchedItem.status != Status.archived) {
                        searchedItem.comments += Comment(TicketStorage.generateID(), commentData.body, UserStorage.getById(user.activeUID)!!.user, 0, mutableListOf(), searchedItem.id)
                        call.respond(HttpStatusCode.OK, "Comment created.")
                        TicketStorage.save()
                    } else call.respond(HttpStatusCode.BadRequest, "Ticket is archived.")
                } else if (searchedItem is Comment) {
                    searchedItem.comments += Comment(TicketStorage.generateID(), commentData.body, UserStorage.getById(user.activeUID)!!.user, 0, mutableListOf(), searchedItem.id)
                    TicketStorage.save()
                    call.respond(HttpStatusCode.OK, "Comment created.")
                } else call.respond(HttpStatusCode.BadRequest, "Id was not found.")
            } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
        } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
    }
}

@KtorDsl
fun Route.routeDelete() {
    delete("/delete") {
        val user = call.sessions.get<UserSession>()
        val deleteData = call.receiveNullable<DeleteData>()

        if (deleteData != null) {
            if (user != null) {
                val searchedItem = TicketStorage.getGlobalByID(deleteData.id)
                if (searchedItem is Ticket) {
                    if (searchedItem.author == UserStorage.getById(user.activeUID)!!.user || UserStorage.getById(user.activeUID)!!.isAdmin) {
                        if (searchedItem.status != Status.archived) {
                            TicketStorage.tickets.remove(searchedItem)
                            call.respond(HttpStatusCode.OK, "Ticked deleted.")
                            TicketStorage.save()
                        } else call.respond(HttpStatusCode.BadRequest, "Ticket is archived.")
                    } else call.respond(HttpStatusCode.BadRequest, "No permission to delete.")
                } else if (searchedItem is Comment) {
                    if (searchedItem.author == UserStorage.getById(user.activeUID)!!.user || UserStorage.getById(user.activeUID)!!.isAdmin) {
                        val v = TicketStorage.getGlobalByID(searchedItem.parent)
                        if (v is Ticket) {
                            v.comments.remove(searchedItem)
                        } else (v as Comment).comments.remove(searchedItem)
                        TicketStorage.save()
                        call.respond(HttpStatusCode.OK, "Comment deleted.")

                    } else call.respond(HttpStatusCode.BadRequest, "No permission to delete.")
                } else call.respond(HttpStatusCode.BadRequest, "Id was not found.")
            } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
        } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
    }
}

@KtorDsl
fun Route.routeCurrentUser() {
    get("/user") {
        val user = call.sessions.get<UserSession>()

        if (user != null) {
            call.respond(UserStorage.getById(user.activeUID)!!.user)
        } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
    }
}
