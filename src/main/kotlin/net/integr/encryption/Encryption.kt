package net.integr.encryption

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

class Encryption {
    companion object {
        private fun hashOnce(password: String): String {
            return BigInteger(1, MessageDigest.getInstance("SHA-512").digest(password.toByteArray())).toString(16)
        }

        fun hash(password: String): String {
            var hash = password

            for (i in 1..32) {
                hash = hashOnce(hash)
            }

            return hash
        }

        fun validateSHA512(password: String): Boolean {
            if (password.length != 128) return false
            if (!password.matches(Regex("[0-9a-f]{128}"))) return false

            return true
        }

        fun generateSalt(): String {
            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return hash(bytes.joinToString("").filter { c -> c != '-' })
        }
    }
}