package com.manu.reeldrop.data.remote

/** Typed failures so the retry engine can tell "try again" from "never retry". */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No route to the server: wrong URL, Termux stopped, wrong Wi-Fi. */
    class Unreachable(message: String, cause: Throwable? = null) : ApiException(message, cause)

    /** Server answered but rejected the request (bad token, client error). */
    class Rejected(message: String, val code: Int) : ApiException(message)

    /** Server answered with a 5xx or an unparsable body. */
    class ServerError(message: String) : ApiException(message)

    /** Request took too long. */
    class Timeout(message: String, cause: Throwable? = null) : ApiException(message, cause)

    /** 404/unknown action: usually a server version mismatch. */
    class NotSupported(message: String) : ApiException(message)

    val isRetryable: Boolean
        get() = when (this) {
            is Unreachable, is ServerError, is Timeout -> true
            is Rejected -> code >= 500 || code == 408 || code == 429
            is NotSupported -> false
        }
}
