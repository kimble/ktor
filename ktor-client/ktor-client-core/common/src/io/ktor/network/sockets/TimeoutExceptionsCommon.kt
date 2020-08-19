/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*

/**
 * This exception is thrown in case connect timeout exceeded.
 */
expect class ConnectTimeoutException(message: String, cause: Throwable? = null) : IOException

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
expect class SocketTimeoutException(message: String, cause: Throwable? = null) : IOException

/**
 * Returns [ByteReadChannel] with [ByteChannel.close] handler that returns [SocketTimeoutException] instead of
 * [SocketTimeoutException].
 */
@InternalAPI
public fun CoroutineScope.mapEngineExceptions(input: ByteReadChannel, request: HttpRequestData): ByteReadChannel {
    if (PlatformUtils.IS_NATIVE) {
        return input
    }

    val replacementChannel = ByteChannelWithMappedExceptions(request)

    writer(channel = replacementChannel) {
        try {
            input.joinTo(replacementChannel, closeOnEnd = true)
        } catch (cause: Throwable) {
            input.cancel(cause)
        }
    }

    return replacementChannel
}

/**
 * Returns [ByteWriteChannel] with [ByteChannel.close] handler that returns [SocketTimeoutException] instead of
 * [SocketTimeoutException].
 */
@InternalAPI
public fun CoroutineScope.mapEngineExceptions(output: ByteWriteChannel, request: HttpRequestData): ByteWriteChannel {
    if (PlatformUtils.IS_NATIVE) {
        return output
    }

    val replacementChannel = ByteChannelWithMappedExceptions(request)

    writer(channel = replacementChannel) {
        try {
            replacementChannel.joinTo(output, closeOnEnd = true)
        } catch (cause: Throwable) {
            replacementChannel.close(cause)
        }
    }

    return replacementChannel
}

/**
 * Creates [ByteChannel] that maps close exceptions (close the channel with [SocketTimeoutException] if asked to
 * close it with [SocketTimeoutException]).
 */
internal expect fun ByteChannelWithMappedExceptions(request: HttpRequestData): ByteChannel