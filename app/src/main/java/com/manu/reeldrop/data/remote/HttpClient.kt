package com.manu.reeldrop.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared OkHttp client factory tuned for a small local server. */
object HttpClient {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // SSE streams and big files need no global cap
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Client used for cheap health probes where a fast failure matters more than patience. */
    val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
