package dev.matthewmorris.cuttracker.data.food

import java.io.IOException

class RateLimitedException : IOException("Too many requests to Open Food Facts this minute")

/** At most [perMinute] calls in any 60 seconds. Over the limit it fails at once rather than queueing. */
class RateLimiter(private val perMinute: Int, private val now: () -> Long) {
    private val calls = ArrayDeque<Long>()

    @Synchronized
    fun acquire() {
        val time = now()
        while (calls.isNotEmpty() && time - calls.first() >= 60_000) calls.removeFirst()
        if (calls.size >= perMinute) throw RateLimitedException()
        calls.addLast(time)
    }
}
