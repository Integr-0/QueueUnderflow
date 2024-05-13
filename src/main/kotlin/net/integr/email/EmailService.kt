package net.integr.email

import net.integr.config.ConfigStorage
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.SimpleEmail
import kotlin.random.Random

class EmailService {
    companion object {
        fun send(code: String, address: String) {
            val email = SimpleEmail()

            email.hostName = "smtp.googlemail.com"
            email.setSmtpPort(465)
            email.setAuthenticator(DefaultAuthenticator(ConfigStorage.INSTANCE!!.emailUsername, ConfigStorage.INSTANCE!!.emailPassword))
            email.isSSLOnConnect = true
            email.setFrom(ConfigStorage.INSTANCE!!.email)
            email.subject = "Queue Underflow verification code"
            email.setMsg(code)
            email.addTo(address)
            email.send()
        }

        fun generateVerify(root: String): Pair<String, Int> {
            var code = Random.nextInt(1000, 9999)

            while(CodeStorage.containsCode(code)) {
                code = Random.nextInt(1000, 9999)
            }

            return Pair("$root/verify?code=$code", code)
        }

        fun verifyEmail(email: String): Boolean {
            return Regex("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$").matches(email)
        }
    }
}