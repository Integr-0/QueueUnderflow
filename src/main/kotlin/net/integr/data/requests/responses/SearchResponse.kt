package net.integr.data.requests.responses

import net.integr.data.tickets.Ticket
import net.integr.data.users.User

class SearchResponse(val tickets: List<Ticket>, val users: List<User>) {
}