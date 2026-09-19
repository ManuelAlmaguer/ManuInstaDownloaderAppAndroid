package com.manu.reeldrop.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Executes [block] and retries transient failures with exponential backoff plus jitter.
 * Deterministic business failures (bad URL, rejected token) are re-thrown immediately.
 */
suspend fun <T> withRetries(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 800L,
    maxDelayMs: Long = 15_000L,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { defaultShouldRetry(it) },
    onAttemptFailed: suspend (attempt: Int, error: Throwable, delayMs: Long) -> Unit = { _, _, _ -> },
    block: suspend (attempt: Int) -> T,
): T {
    var attempt = 1
    var delayMs = initialDelayMs
    while (true) {
        try {
            return block(attempt)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            if (attempt >= maxAttempts || !shouldRetry(error)) throw error
            val jitter = Random.nextLong(0, (delayMs / 3).coerceAtLeast(1))
            val wait = (delayMs + jitter).coerceAtMost(maxDelayMs)
            onAttemptFailed(attempt, error, wait)
            delay(wait)
            delayMs = (delayMs * factor).toLong().coerceAtMost(maxDelayMs)
            attempt++
        }
    }
}

fun defaultShouldRetry(error: Throwable): Boolean = when (error) {
    is ApiException -> error.isRetryable
    is java.io.IOException -> true
    else -> false
}
