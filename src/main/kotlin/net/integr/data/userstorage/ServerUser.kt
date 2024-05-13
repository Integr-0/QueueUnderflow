package net.integr.data.userstorage

import net.integr.data.webdata.User

class ServerUser(val username: String, val email: String, val passwordHash: String, val salt: String, val user: User) {
}