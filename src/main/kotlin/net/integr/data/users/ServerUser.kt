package net.integr.data.users

class ServerUser(val username: String, val email: String, val passwordHash: String, val salt: String, val user: User, val isAdmin: Boolean) {
}