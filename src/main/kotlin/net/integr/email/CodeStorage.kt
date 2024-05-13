package net.integr.email

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

class CodeStorage {
    companion object {
        var awaiting: MutableList<EmailVerificationPiece> = mutableListOf()

        fun load() {
            val f = File("./data/codes.json")
            if (!f.exists()) save()

            val json = f.readText()
            awaiting = GsonBuilder().setPrettyPrinting().create().fromJson(json, Array<EmailVerificationPiece>::class.java).toMutableList()
        }

        fun save() {
            val json = GsonBuilder().setPrettyPrinting().create().toJson(awaiting)
            Files.createDirectories(Path("./data"))
            File("./data/codes.json").writeText(json)
        }

        fun containsCode(code: Int): Boolean {
            for (piece in awaiting) {
                if (piece.verificationCode == code) return true
            }

            return false
        }

        fun getByCode(code: Int): EmailVerificationPiece? {
            for (piece in awaiting) {
                if (piece.verificationCode == code) return piece
            }

            return null
        }

        fun getFromUsername(username: String): EmailVerificationPiece? {
            for (user in awaiting) {
                if (user.username == username) {
                    return user
                }
            }

            return null
        }

        fun getFromEmail(email: String): EmailVerificationPiece? {
            for (user in awaiting) {
                if (user.email == email) {
                    return user
                }
            }

            return null
        }
    }
}