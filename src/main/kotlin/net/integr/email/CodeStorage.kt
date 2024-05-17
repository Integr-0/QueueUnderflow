package net.integr.email

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

class CodeStorage {
    companion object {
        var awaiting: MutableList<EmailVerificationPiece> = mutableListOf()

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