package net.integr.data.webdata

class Comment(val id: Long, val body: String, val author: User, val score: Int, val comments: List<Comment>) {
}