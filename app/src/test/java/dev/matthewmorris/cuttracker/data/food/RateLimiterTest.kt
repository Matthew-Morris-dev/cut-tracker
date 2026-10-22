package dev.matthewmorris.cuttracker.data.food

import org.junit.Test

class RateLimiterTest {
    @Test fun `allows the limit, refuses one more, and frees up after a minute`() {
        var now = 0L
        val limiter = RateLimiter(perMinute = 10) { now }
        repeat(10) { limiter.acquire() }
        runCatching { limiter.acquire() }.also { check(it.exceptionOrNull() is RateLimitedException) }

        now = 59_999
        runCatching { limiter.acquire() }.also { check(it.exceptionOrNull() is RateLimitedException) }

        now = 60_000
        limiter.acquire()
    }
}
