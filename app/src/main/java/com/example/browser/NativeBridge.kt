package com.example.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Keep
class NativeBridge(
    private val context: Context,
    private val webView: WebView,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "NativeBridge"
        private const val LOCAL_PORT = 8085
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        
        @Volatile
        private var serverSocket: ServerSocket? = null
        private val activeConnections = ConcurrentHashMap.newKeySet<Socket>()
        
        @Volatile
        private var serverJob: Job? = null
        
        @Volatile
        private var isEngineRunning = false
        
        init {
            try {
                System.loadLibrary("native_media_bridge")
                Log.i(TAG, "Native C++ dependencies successfully linked to coordination engine!")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed loading JNI library: native_media_bridge", e)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        startLocalWebSocketBroadcastDaemon()
    }

    // Direct JNI hardware streaming anchors
    private external fun startNativeAudioCapture()
    private external fun stopNativeAudioCapture()

    @JavascriptInterface
    fun initializeHardwareStream(transactionId: String, origin: String) {
        Log.d(TAG, "Received hardware initializer handshake. Origin: $origin. Transaction: $transactionId")
        
        mainHandler.post {
            // Assert and check Android OS context permissions
            val recordPermissionCheck = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (recordPermissionCheck) {
                executeDirectHardwareStream()
            } else {
                Log.w(TAG, "OS hardware mic permission physically missing in this container context.")
                webView.evaluateJavascript(
                    "if(window.activePermissionPromise?.reject) { window.activePermissionPromise.reject(new DOMException('OS Recording Permission missing', 'NotAllowedError')); }",
                    null
                )
            }
        }
    }

    private fun executeDirectHardwareStream() {
        scope.launch(Dispatchers.IO) {
            try {
                if (!isEngineRunning) {
                    isEngineRunning = true
                    Log.i(TAG, "Bootstrapping low-latency NDK direct AAudio drivers...")
                    startNativeAudioCapture()
                }
                
                withContext(Dispatchers.Main) {
                    // Inject loopback client WS feed address back to high performance page context
                    val streamUrl = "ws://127.0.0.1:$LOCAL_PORT/audio-stream"
                    webView.evaluateJavascript(
                        "if (window.onNativeHardwareStreamReady) { window.onNativeHardwareStreamReady('$streamUrl'); }",
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error aligning dynamic hardware pipeline", e)
            }
        }
    }

    /**
     * Start a ultra light TCP listener accepting the WebSocket upgrade flow.
     * Compiles on basic JVM with zero external runtime package dependencies!
     */
    private fun startLocalWebSocketBroadcastDaemon() {
        if (serverSocket != null) {
            Log.d(TAG, "Local WebSocket stream proxy server is already running. Reusing existing instance.")
            return
        }
        synchronized(NativeBridge::class.java) {
            if (serverSocket != null) return
            
            serverJob = scope.launch(Dispatchers.IO) {
                try {
                    serverSocket = ServerSocket(LOCAL_PORT)
                    Log.i(TAG, "Lightweight Local WebSocket stream proxy listening on 127.0.0.1:$LOCAL_PORT")
                    
                    while (isActive) {
                        val clientSocket = serverSocket?.accept() ?: break
                        scope.launch(Dispatchers.IO) {
                            handleClientConnection(clientSocket)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket local stream server encountered exception during launch", e)
                }
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            Log.d(TAG, "Client connected: ${socket.remoteSocketAddress}")
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            
            // 1. Process HTTP WebSocket Handshake headers
            val key = readHandshakeKey(inputStream)
            if (key == null) {
                Log.e(TAG, "Key missing from handshake stream. Disconnecting.")
                socket.close()
                return;
            }

            // 2. Perform base64 SHA-1 websocket key hashing matching official specs
            val combined = key + WEBSOCKET_GUID
            val sha1Bytes = MessageDigest.getInstance("SHA-1").digest(combined.toByteArray(Charsets.UTF_8))
            val acceptString = Base64.encodeToString(sha1Bytes, Base64.NO_WRAP)

            // 3. Complete HTTP Upgrade response
            val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $acceptString\r\n\r\n"
                    
            outputStream.write(response.toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            Log.i(TAG, "Local loopback Handshake complete. Multiplexing binary sound bytes.")

            activeConnections.add(socket)

            // 4. Block thread context to monitor client socket reading (closed flags / drop)
            val tempBuffer = ByteArray(1024)
            while (true) {
                val read = inputStream.read(tempBuffer)
                if (read == -1) break // Connection dropped safely
            }
        } catch (e: Exception) {
            Log.d(TAG, "Connection lifecycle terminated: ${e.message}")
        } finally {
            activeConnections.remove(socket)
            try { socket.close() } catch (ignored: Exception) {}
            Log.d(TAG, "Active connections remaining in list: ${activeConnections.size}")
            
            // Clean up core hardware capture if all interfaces disconnect
            if (activeConnections.isEmpty() && isEngineRunning) {
                scope.launch(Dispatchers.IO) {
                    stopCoreCaptureEngine()
                }
            }
        }
    }

    private fun readHandshakeKey(stream: InputStream): String? {
        val reader = stream.bufferedReader()
        var line: String?
        var wsKey: String? = null
        
        while (true) {
            line = reader.readLine()
            if (line.isNullOrEmpty()) break
            
            if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                wsKey = line.substringAfter(":").trim()
            }
        }
        return wsKey
    }

    /**
     * Send direct binary frames through standard socket stream safely.
     * Follows the official RFC 6455 framing specification precisely.
     */
    private fun writeWebSocketBinaryFrame(stream: OutputStream, payload: ByteArray) {
        synchronized(stream) {
            val length = payload.size
            stream.write(0x82) // 0x82: FIN bit set (1000 0000) and Binary Opcode (0010)

            if (length <= 125) {
                stream.write(length)
            } else if (length < 65536) {
                stream.write(126)
                stream.write((length shr 8) and 0xFF)
                stream.write(length and 0xFF)
            } else {
                stream.write(127)
                // Write standard length block
                for (i in 7 downTo 0) {
                    stream.write(((length.toLong() shr (i * 8)) and 0xFF).toInt())
                }
            }
            stream.write(payload)
            stream.flush()
        }
    }

    /**
     * External callback JNI bridge target triggered from low latency AAudio thread.
     */
    @Keep
    fun onRawPcmBufferAvailable(pcmData: ByteArray) {
        if (activeConnections.isEmpty()) return
        
        // Push raw binary frame to each active receiver safely via binary sockets
        for (conn in activeConnections) {
            if (conn.isConnected && !conn.isClosed) {
                try {
                    val out = conn.getOutputStream()
                    writeWebSocketBinaryFrame(out, pcmData)
                } catch (e: Exception) {
                    Log.w(TAG, "Frame routing dropped for socket destination: ${e.message}")
                }
            }
        }
    }

    private fun stopCoreCaptureEngine() {
        if (isEngineRunning) {
            isEngineRunning = false
            Log.i(TAG, "All loopback sockets closed. Stopping hardware recording...")
            try {
                stopNativeAudioCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Exception during stopNativeAudioCapture", e)
            }
        }
    }

    fun destroy() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (ignored: Exception) {}
        
        for (conn in activeConnections) {
            try { conn.close() } catch (ignored: Exception) {}
        }
        activeConnections.clear()
        stopCoreCaptureEngine()
    }
}
