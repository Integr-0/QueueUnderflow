package net.integr.data.tickets.comment

import net.integr.data.users.User

class Comment(val id: Long, val body: String, val author: User, val score: Int, val comments: MutableList<Comment>, val parent: Long) {
    fun containsCommentID(id: Long): Boolean {
        for (comment in this.comments) {
            if (comment.id == id) {
                return true
            }

            if (comment.containsCommentID(id)) return true
        }

        return false
    }

    fun getCommentById(id: Long): Comment? {
        for (comment in this.comments) {
            if (comment.id == id) {
                return comment
            }

            val c = comment.getCommentById(id)
            if (c != null) return c
        }

        return null
    }
}