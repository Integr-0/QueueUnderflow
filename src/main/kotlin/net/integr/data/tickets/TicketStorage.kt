package net.integr.data.tickets

import com.google.gson.GsonBuilder
import net.integr.data.tickets.comment.Comment
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.math.min
import kotlin.random.Random

class TicketStorage {
    companion object {
        var tickets: MutableList<Ticket> = mutableListOf()

        fun load() {
            val f = File("./data/tickets.json")
            if (!f.exists()) save()

            val json = f.readText()
            tickets = GsonBuilder().setPrettyPrinting().create().fromJson(json, Array<Ticket>::class.java).toMutableList()
        }

        fun save() {
            val json = GsonBuilder().setPrettyPrinting().create().toJson(tickets)
            Files.createDirectories(Path("./data"))
            File("./data/tickets.json").writeText(json)
        }

        fun getAmount(amount: Int, offset: Int): List<Ticket> {
            return tickets.sortedBy { it.createdAt }.subList(min(tickets.size, offset), min(tickets.size, offset+amount))
        }

        fun generateID(): Long {
            var id = Random.nextLong(1000000000, 99999999999)

            while (containsID(id) || containsGlobalCommentID(id)) {
                id = Random.nextLong(1000000000, 99999999999)
            }

            return id
        }

        private fun containsID(id: Long): Boolean {
            for (ticket in tickets) {
                if (ticket.id == id) {
                    return true
                }
            }

            return false
        }

        private fun containsGlobalCommentID(id: Long): Boolean {
            for (ticket in tickets) {
                for (comment in ticket.comments) {
                    if (comment.containsCommentID(id)) return true
                }
            }

            return false
        }

        fun getById(id: Long): Ticket? {
            for (ticket in tickets) {
                if (ticket.id == id) {
                    return ticket
                }
            }

            return null
        }

        fun getGlobalCommentById(id: Long): Comment? {
            for (ticket in tickets) {
                for (comment in ticket.comments) {
                    if (comment.id == id) return comment

                    val c = comment.getCommentById(id)
                    if (c != null) return c
                }
            }

            return null
        }

        fun getGlobalByID(id: Long): Any? {
            return getById(id) ?: getGlobalCommentById(id)
        }
    }
}