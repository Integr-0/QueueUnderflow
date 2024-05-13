package net.integr.email

class EmailVerificationPiece(val creation: Long, val email: String, val verificationCode: Int, val hashedPass: String, val salt: String, val username: String) {
}