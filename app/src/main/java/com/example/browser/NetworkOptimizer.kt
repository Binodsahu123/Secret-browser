package com.example.browser

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.example.nativenetworkengine.NativeNetworkEngine

object NetworkOptimizer {
    private const val TAG = "NetworkOptimizer"
    private val prefetchCache = ConcurrentHashMap<String, String>()
    private val customDnsCache = ConcurrentHashMap<String, List<InetAddress>>()

    // Highly optimized Connection Pool: Up to 30 idle connections, keeps connections alive for 5 minutes
    val activeConnectionPool = okhttp3.ConnectionPool(30, 5, TimeUnit.MINUTES)

    // Parallel Request dispatcher
    val optimizedDispatcher = okhttp3.Dispatcher().apply {
        maxRequests = 128
        maxRequestsPerHost = 32
    }

    // Smart DNS with pre-resolve cache to bypass standard lookup latencies
    val smartDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val cached = customDnsCache[hostname]
            if (cached != null) {
                return cached
            }
            return try {
                val resolved = Dns.SYSTEM.lookup(hostname)
                customDnsCache[hostname] = resolved
                resolved
            } catch (e: Exception) {
                Log.w(TAG, "DNS resolution failed for $hostname, using fallback system query.")
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    /**
     * Natively-backed Interceptor to measure network throughput and optimize payloads.
     */
    private val trafficTrackerInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        
        try {
            val host = request.url.host
            val responseBody = response.peekBody(1024 * 64)
            val bytes = responseBody.contentLength()
            
            // Invoke native tracking logic to calculate zero-copy metrics
            val jsonResult = NativeNetworkEngine.trackTraffic(host, bytes)
            Log.d(TAG, "Native traffic stats registered: $jsonResult")
        } catch (e: Exception) {
            // Graceful fallback if anything goes wrong during metric extraction
        }
        
        response
    }

    fun prefetchDns(hosts: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (host in hosts) {
                try {
                    val resolved = InetAddress.getAllByName(host).toList()
                    customDnsCache[host] = resolved
                    Log.d(TAG, "DNS prefetch success for: $host -> $resolved")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed DNS prefetching for $host")
                }
            }
        }
    }

    /**
     * Standard entrypoint to optimize any OkHttpClient Builder in the browser system.
     */
    fun optimizeClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return builder
            .connectionPool(activeConnectionPool)
            .dispatcher(optimizedDispatcher)
            .dns(smartDns)
            .addInterceptor(trafficTrackerInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
    }
}
