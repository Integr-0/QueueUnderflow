package net.integr

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import net.integr.Api.Companion.routeApi
import net.integr.Frontend.Companion.routeFrontend
import net.integr.config.ConfigStorage
import net.integr.cookie.UserSession
import net.integr.data.requests.*
import net.integr.data.requests.responses.SearchResponse
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
import kotlin.streams.toList

fun Application.configureRouting() {
    routing {
        routeApi()
        routeFrontend()
    }
}

class Frontend {
    companion object {
        @KtorDsl
        fun Route.routeFrontend() {

        }
    }
}

class Api {
    companion object {
        @KtorDsl
        fun Route.routeApi() {
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

            routeSearch()

            routeDownVote()
            routeUpVote()
        }

        @KtorDsl
        fun Route.routeLogin() {
            post("/api/login") {
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
            post("/api/signup") {
                try {
                    val signupData = call.receiveNullable<SignupData>() // Get the signup json data

                    if (signupData != null && signupData.username.isNotEmpty() && signupData.username.matches(Regex("[a-z0-9_@]*")) && signupData.username.length >= 2 && signupData.password.isNotEmpty() && signupData.password.isNotEmpty()) {
                        val username = signupData.username
                        if (UserStorage.getFromUsername(username) == null && CodeStorage.getFromUsername(username) == null) {
                            if (username.toCharArray().toList().stream().filter { it.isUpperCase() }.toList().isEmpty()) {
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
                            } else call.respond(HttpStatusCode.BadRequest, "Username is not lowercase.")
                        } else call.respond(HttpStatusCode.BadRequest, "Username already exist.")
                    } else call.respond(HttpStatusCode.BadRequest, "Missing/Invalid data.")
                } catch (e: NullPointerException) {
                    call.respond(HttpStatusCode.BadRequest, "Missing data.")
                }
            }
        }

        @KtorDsl
        fun Route.routeVerify() {
            get("/api/verify") {
                val code = call.parameters["code"]
                if (code != null) {
                    try {
                        val savedCode = CodeStorage.getByCode(code.toInt()) // Get the auth code from storage

                        if (savedCode != null) {
                            val user = User(UserStorage.generateID(), savedCode.username, savedCode.username, savedCode.creation) // Create user
                            UserStorage.users += ServerUser(savedCode.email, savedCode.hashedPass, savedCode.salt, user, false)
                            CodeStorage.awaiting.remove(savedCode) // Delete code
                            CodeStorage.save() // Save the data
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
            post("/api/logout") {
                val account = call.sessions.get<UserSession>() // Get the active user

                if (account != null) {
                    call.sessions.clear<UserSession>() // Log out
                    call.respond(HttpStatusCode.OK, "Logged Out.")
                } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
            }
        }

        @KtorDsl
        fun Route.routeDeleteAccount() {
            delete("/api/delete_account") {
                val account = call.sessions.get<UserSession>() // Get the active user

                if (account != null) {
                    call.sessions.clear<UserSession>() // Log out
                    UserStorage.users.remove(UserStorage.getById(account.activeUID)) // Delete the user
                    UserStorage.save() // Save
                    call.respond(HttpStatusCode.OK, "Deleted.")
                } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
            }
        }

        @KtorDsl
        fun Route.routeTickets() {
            get("/api/tickets") {
                val limit = call.parameters["limit"]
                val offset = call.parameters["offset"]
                val noCom = call.parameters.contains("nocom") // Get the data

                if (limit != null) {
                    try {
                        val setLimit = limit.toInt()
                        if (setLimit > 0) {
                            val tickets: MutableList<Ticket> = mutableListOf()
                            var os = 0
                            try {
                                if (offset != null) os = max(0, offset.toInt()) // Manage the offset
                                if (noCom) { // Manage removing the comments
                                    TicketStorage.getAmount(setLimit, os).forEach {
                                        val t = it.displayNoComCopy()
                                        tickets += t
                                    }
                                } else tickets.addAll(TicketStorage.getAmount(setLimit, os)) // Get tickets
                                call.respond(tickets) // Return the found tickets
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
            get("/api/tickets/{id}") {
                val id = call.parameters["id"]

                if (id != null) {
                    try {
                        val setId = id.toLong()
                        val ticket = TicketStorage.getById(setId) // Get the queried ticket
                        if (ticket != null) {
                            call.respond(ticket) // Send it to the user
                        } else call.respond(HttpStatusCode.BadRequest, "Ticket was not found.")
                    } catch (e: NumberFormatException) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid id.")
                    }
                } else call.respond(HttpStatusCode.BadRequest, "Invalid id.")
            }
        }

        @KtorDsl
        fun Route.routeUsers() {
            get("/api/users/{id}") {
                val id = call.parameters["id"]

                if (id != null) {
                    try {
                        val setId = id.toLong()
                        val user = UserStorage.getById(setId) // Get the queried user
                        if (user != null) {
                            call.respond(user.user) // Send it to the user
                        } else call.respond(HttpStatusCode.BadRequest, "User was not found.")
                    } catch (e: NumberFormatException) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid id.")
                    }
                } else call.respond(HttpStatusCode.BadRequest, "Invalid id.")
            }
        }

        @KtorDsl
        fun Route.routePost() {
            post("/api/post") {
                val user = call.sessions.get<UserSession>()
                val creationData = call.receiveNullable<CreateData>() // Get the data

                if (creationData != null && creationData.title.isNotEmpty() && creationData.title.length >= 6 && creationData.body.isNotEmpty() && creationData.body.length >= 10) {
                    if (user != null) {
                        val ticket = Ticket(TicketStorage.generateID(), creationData.title, creationData.body, UserStorage.getById(user.activeUID)!!.user, mutableListOf(), mutableListOf(), mutableListOf(), creationData.tags, Status.unsolved, System.currentTimeMillis()) // Create a new ticket
                        TicketStorage.tickets += ticket
                        TicketStorage.save() // Save the data
                        call.respond(ticket)
                    } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
                } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
            }
        }

        @KtorDsl
        fun Route.routeComment() {
            post("/api/comment") {
                val user = call.sessions.get<UserSession>()
                val commentData = call.receiveNullable<CommentData>() // Get the data

                if (commentData != null && commentData.body.isNotEmpty()) {
                    if (user != null) {
                        val searchedItem = TicketStorage.getGlobalByID(commentData.id) // Get the object
                        if (searchedItem is Ticket) { // Handle ticket
                            if (searchedItem.status != Status.archived) {
                                searchedItem.comments += Comment(TicketStorage.generateID(), commentData.body, UserStorage.getById(user.activeUID)!!.user, 0, mutableListOf(), searchedItem.id) // Create the comment
                                call.respond(HttpStatusCode.OK, "Comment created.")
                                TicketStorage.save() // Save the ticket
                            } else call.respond(HttpStatusCode.BadRequest, "Ticket is archived.")
                        } else if (searchedItem is Comment) { // Handle comment
                            searchedItem.comments += Comment(TicketStorage.generateID(), commentData.body, UserStorage.getById(user.activeUID)!!.user, 0, mutableListOf(), searchedItem.id) // Create the comment
                            TicketStorage.save() // Save the comment
                            call.respond(HttpStatusCode.OK, "Comment created.")
                        } else call.respond(HttpStatusCode.BadRequest, "Id was not found.")
                    } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
                } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
            }
        }

        @KtorDsl
        fun Route.routeDelete() {
            delete("/api/delete") {
                val user = call.sessions.get<UserSession>()
                val deleteData = call.receiveNullable<DeleteData>() // Get the user and the item to delete

                if (deleteData != null) {
                    if (user != null) {
                        val searchedItem = TicketStorage.getGlobalByID(deleteData.id) // Get the item from storage
                        if (searchedItem is Ticket) { // Handle ticket
                            if (searchedItem.author == UserStorage.getById(user.activeUID)!!.user || UserStorage.getById(user.activeUID)!!.isAdmin) {
                                if (searchedItem.status != Status.archived) {
                                    TicketStorage.tickets.remove(searchedItem) // Delete the ticket
                                    TicketStorage.save() // Save the data
                                    call.respond(HttpStatusCode.OK, "Ticked deleted.")
                                } else call.respond(HttpStatusCode.BadRequest, "Ticket is archived.")
                            } else call.respond(HttpStatusCode.BadRequest, "No permission to delete.")
                        } else if (searchedItem is Comment) { // Handle comment
                            if (searchedItem.author == UserStorage.getById(user.activeUID)!!.user || UserStorage.getById(user.activeUID)!!.isAdmin) {
                                val v = TicketStorage.getGlobalByID(searchedItem.parent) // Get the comment parent
                                if (v is Ticket) {
                                    v.comments.remove(searchedItem) // Delete the comment
                                } else (v as Comment).comments.remove(searchedItem)
                                TicketStorage.save() // Save the data
                                call.respond(HttpStatusCode.OK, "Comment deleted.")

                            } else call.respond(HttpStatusCode.BadRequest, "No permission to delete.")
                        } else call.respond(HttpStatusCode.BadRequest, "Id was not found.")
                    } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
                } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
            }
        }

        @KtorDsl
        fun Route.routeCurrentUser() {
            get("/api/user") {
                val user = call.sessions.get<UserSession>() // Get the current user

                if (user != null) {
                    call.respond(UserStorage.getById(user.activeUID)!!.user) // Send the data
                } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
            }
        }

        @KtorDsl
        fun Route.routeSearch() {
            get("/api/search") {
                val query = call.parameters["query"]
                val limit = call.parameters["limit"]

                if (!query.isNullOrEmpty()) {
                    if (limit != null) {
                        try {
                            val setLimit = limit.toInt()
                            val users = UserStorage.getByQuery(query, setLimit)
                            val tickets = TicketStorage.getByQuery(query, setLimit)

                            call.respond(SearchResponse(tickets, users)) // Respond with the data
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid limit.")
                        }
                    } else {
                        val users = UserStorage.getByQuery(query)
                        val tickets = TicketStorage.getByQuery(query)

                        call.respond(SearchResponse(tickets, users)) // Respond with the data
                    }
                } else call.respond(HttpStatusCode.BadRequest, "No query present.")
            }
        }

        @KtorDsl
        fun Route.routeUpVote() {
            post("/api/upvote") {
                val user = call.sessions.get<UserSession>()

                if (user != null) {
                    val data = call.receiveNullable<VoteData>()
                    if (data != null) {
                        val ticket = TicketStorage.getById(data.id)
                        if (ticket != null) {
                            if (!ticket.upVoters.contains(user.activeUID)) {
                                ticket.upVoters.add(user.activeUID)
                                if (ticket.downVoters.contains(user.activeUID)) ticket.downVoters.remove(user.activeUID)

                                TicketStorage.save()
                                call.respond(HttpStatusCode.OK, "Upvoted the ticket.")
                            } else call.respond(HttpStatusCode.BadRequest, "Already upvoting this ticket.")
                        } else call.respond(HttpStatusCode.BadRequest, "Ticket not found.")
                    } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
                } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
            }
        }

        @KtorDsl
        fun Route.routeDownVote() {
            post("/api/downvote") {
                val user = call.sessions.get<UserSession>()

                if (user != null) {
                    val data = call.receiveNullable<VoteData>()
                    if (data != null) {
                        val ticket = TicketStorage.getById(data.id)
                        if (ticket != null) {
                            if (!ticket.downVoters.contains(user.activeUID)) {
                                ticket.downVoters.add(user.activeUID)
                                if (ticket.upVoters.contains(user.activeUID)) ticket.upVoters.remove(user.activeUID)

                                TicketStorage.save()
                                call.respond(HttpStatusCode.OK, "Downvoted the ticket.")
                            } else call.respond(HttpStatusCode.BadRequest, "Already downvoting this ticket.")
                        } else call.respond(HttpStatusCode.BadRequest, "Ticket not found.")
                    } else call.respond(HttpStatusCode.BadRequest, "Invalid data.")
                } else call.respond(HttpStatusCode.BadRequest, "Not logged in.")
            }
        }
    }
}

