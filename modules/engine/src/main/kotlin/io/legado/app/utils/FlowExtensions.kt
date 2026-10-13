package io.legado.app.utils

import io.legado.app.BuildConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore

fun Throwable.printOnDebug() {
    if (BuildConfig.DEBUG) {
        printStackTrace()
    }
}

/**
 * 并发 map（自原 FlowExtensions 提取，纯 JVM）
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.mapAsync(
    concurrency: Int,
    crossinline transform: suspend (T) -> R,
): Flow<R> = if (concurrency == 1) {
    map { transform(it) }
} else {
    Semaphore(concurrency).let { semaphore ->
        channelFlow {
            collect {
                semaphore.acquire()
                send(async { transform(it) })
            }
        }.map {
            it.await()
        }.onEach { semaphore.release() }
    }.buffer(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T, R> Flow<T>.mapAsyncIndexed(
    concurrency: Int,
    crossinline transform: suspend (index: Int, T) -> R,
): Flow<R> = if (concurrency == 1) {
    mapIndexed { index, value ->
        transform(index, value)
    }
} else {
    Semaphore(concurrency).let { semaphore ->
        channelFlow {
            var index = 0
            collect {
                semaphore.acquire()
                val i = index++
                send(async { transform(i, it) })
            }
        }.map {
            it.await()
        }.onEach { semaphore.release() }
    }.buffer(0)
}

/**
 * 并发收集（原 FlowExtensions 提取）
 */
fun <T> Flow<T>.onEachParallel(concurrency: Int, action: suspend (T) -> Unit): Flow<T> =
    mapAsync(concurrency) {
        action(it)
        it
    }

inline fun <T, R> Flow<T>.mapIndexed(
    crossinline action: suspend (index: Int, T) -> R,
): Flow<R> = kotlinx.coroutines.flow.flow {
    var index = 0
    collect { value ->
        emit(action(index++, value))
    }
}
