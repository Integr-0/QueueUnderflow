package net.integr.data.userstorage

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.random.Random

class UserStorage {
    companion object {
        var users: MutableList<ServerUser> = mutableListOf()

        fun load() {
            val f = File("./data/users.json")
            if (!f.exists()) save()

            val json = f.readText()
            users = GsonBuilder().create().fromJson(json, Array<ServerUser>::class.java).toMutableList()
        }

        fun save() {
            val json = GsonBuilder().setPrettyPrinting().create().toJson(users)
            Files.createDirectories(Path("./data"))
            File("./data/users.json").writeText(json)
        }

        fun getFromUsername(username: String): ServerUser? {
            for (user in users) {
                if (user.username == username) {
                    return user
                }
            }

            return null
        }

        fun getFromEmail(email: String): ServerUser? {
            for (user in users) {
                if (user.email == email) {
                    return user
                }
            }

            return null
        }

        fun generateID(): Long {
            var id = Random.nextLong(1000000000, 99999999999)

            while (containsID(id)) {
                id = Random.nextLong(1000000000, 99999999999)
            }

            return id
        }

        private fun containsID(id: Long): Boolean {
            for (user in users) {
                if (user.user.id == id) {
                    return true
                }
            }

            return false
        }
    }
}