package net.integr.data.webdata


class Ticket(val id: Long, val title: String, val body: String, val author: User, val comments: List<Comment>, val score: Int, val tags: List<Tag>, val status: Status) {

}