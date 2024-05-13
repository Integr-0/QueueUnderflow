package net.integr.data.tickets

import net.integr.data.tickets.comment.Comment
import net.integr.data.users.User


class Ticket(val id: Long, val title: String, val body: String, val author: User, val comments: MutableList<Comment>, val score: Int, val tags: List<Int>, val status: Int, val createdAt: Long) {
    fun displayNoComCopy(): Ticket {
        return Ticket(id, title, body, author, mutableListOf(), score, tags, status, createdAt)
    }
}