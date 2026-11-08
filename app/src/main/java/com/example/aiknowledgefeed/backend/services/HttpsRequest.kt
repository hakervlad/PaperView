package com.example.aiknowledgefeed.backend.services

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class HttpPayload(val status: Int, val bytes: ByteArray)

/** No logging; cancellation closes the socket, and every response has a byte limit. */
internal suspend fun OkHttpClient.fetch(request: Request, maxBytes: Int): HttpPayload = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                try {
                    val output = ByteArrayOutputStream()
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(8192)
                        while (continuation.isActive) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > maxBytes) throw ConnectionFailure("invalid_response")
                            output.write(buffer, 0, count)
                        }
                    }
                    if (continuation.isActive) continuation.resume(HttpPayload(response.code, output.toByteArray()))
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        }
    })
}
