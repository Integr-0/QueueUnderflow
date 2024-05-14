package net.integr.data.tickets.extras

class Status {
    companion object {
        val solved = 1 shl 1
        val unsolved = 1 shl 2
        val archived =  1 shl 3
    }
}
