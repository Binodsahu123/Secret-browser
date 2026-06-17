package com.example.browser

import android.util.Log
import kotlinx.coroutines.*
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean

enum class Priority {
    HIGH, NORMAL, LOW
}

data class ScheduledRequest(
    val id: String,
    val url: String,
    val priority: Priority,
    val timestamp: Long = System.currentTimeMillis(),
    val action: suspend () -> Unit
) : Comparable<ScheduledRequest> {
    override fun compareTo(other: ScheduledRequest): Int {
        if (this.priority != other.priority) {
            return this.priority.ordinal.compareTo(other.priority.ordinal) // LOWER ordinal is HIGHER priority (0: HIGH, 1: NORMAL, 2: LOW)
        }
        return this.timestamp.compareTo(other.timestamp)
    }
}

object RequestScheduler {
    private const val TAG = "RequestScheduler"
    private val requestScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = PriorityQueue<ScheduledRequest>()
    private val isProcessing = AtomicBoolean(false)

    @Synchronized
    fun scheduleRequest(url: String, priority: Priority, task: suspend () -> Unit) {
        val req = ScheduledRequest(
            id = java.util.UUID.randomUUID().toString(),
            url = url,
            priority = priority,
            action = task
        )
        queue.offer(req)
        Log.d(TAG, "Scheduled network request with priority $priority for URL: $url")
        triggerProcessQueue()
    }

    private fun triggerProcessQueue() {
        if (isProcessing.compareAndSet(false, true)) {
            requestScope.launch {
                processQueue()
            }
        }
    }

    private suspend fun processQueue() {
        try {
            while (true) {
                val nextReq = synchronized(this) {
                    if (queue.isEmpty()) {
                        isProcessing.set(false)
                        return
                    }
                    queue.poll()
                }

                if (nextReq != null) {
                    executeRequestWithRetries(nextReq)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Queue worker encountered unexpected crash", e)
            isProcessing.set(false)
        }
    }

    private suspend fun executeRequestWithRetries(req: ScheduledRequest) {
        Log.d(TAG, "Processing scheduled request Priority=${req.priority} for URL: ${req.url}")
        var retries = 3
        var success = false
        while (retries > 0 && !success) {
            try {
                req.action()
                success = true
                Log.d(TAG, "Successfully executed priority request: ${req.url}")
            } catch (e: CancellationException) {
                Log.w(TAG, "Request cancelled for URL: ${req.url}")
                throw e
            } catch (e: Exception) {
                retries--
                Log.w(TAG, "Error executing request ${req.url}, retries remaining: $retries. Error: ${e.localizedMessage}")
                if (retries > 0) {
                    delay(1500) // Adaptive backoff delay
                }
            }
        }
    }
}
