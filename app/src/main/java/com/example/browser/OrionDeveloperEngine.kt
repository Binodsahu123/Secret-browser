package com.example.browser

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Central state, telemetry registry, and live diagnostics runner for Orion Developer Mode.
 * Tracks performance, failure traces, web views, networks, downloads, voice, desktop mode, extensions, and engines.
 * Stores sliding windows of the last 100 events for errors, permissions, extensions, downloads, and voice commands.
 */
object OrionDeveloperEngine {

    // Global developer mode state toggle
    private val _isDeveloperModeEnabled = MutableStateFlow(false)
    val isDeveloperModeEnabled = _isDeveloperModeEnabled.asStateFlow()

    // 1. Sliding buffers of telemetry (stores last 100 events)
    data class ErrorEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val component: String,
        val message: String,
        val level: String = "ERROR", // "ERROR", "WARNING"
        val location: String
    )
    val errorLogs = mutableStateListOf<ErrorEvent>()

    data class PermissionEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val requester: String,
        val permission: String,
        val decision: String, // "GRANTED", "DENIED", "PROMPTED"
        val layer: String // "Android OS", "Browser UI", "WebView Bridge"
    )
    val permissionEvents = mutableStateListOf<PermissionEvent>()

    data class ExtensionEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val extensionId: String,
        val extensionName: String,
        val eventType: String, // "LOADED", "INJECTED", "DOM_MODIFY", "API_CALL", "FAILED"
        val detail: String
    )
    val extensionEvents = mutableStateListOf<ExtensionEvent>()

    data class DownloadEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val filename: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val eventType: String, // "START", "CHUNK_COMPLETE", "PAUSE", "RESUME", "ERROR", "SUCCESS"
        val speed: String
    )
    val downloadEvents = mutableStateListOf<DownloadEvent>()

    data class VoiceEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val eventType: String, // "WAKE_WORD", "RECOGNITION", "INTENT_PARSE", "ROUTING", "ERROR"
        val message: String,
        val confidence: Float = 0.95f
    )
    val voiceEvents = mutableStateListOf<VoiceEvent>()

    // Legacy permission log support (for backward compatibility)
    data class PermissionLog(
        val timestamp: Long = System.currentTimeMillis(),
        val origin: String,
        val permission: String,
        val browserPermission: String,
        val androidPermission: String,
        val webViewPermission: String,
        val mediaStreamStatus: String,
        val failurePoint: String
    )
    val permissionLogs = mutableStateListOf<PermissionLog>()

    // Legacy extension statuses
    data class ExtensionStatus(
        val id: String,
        val name: String,
        val manifestLoaded: Boolean = true,
        val permissionsLoaded: Boolean = true,
        val contentScriptInjected: String = "Injected",
        val domModification: String = "Success",
        val runtimeApiStatus: String = "Success",
        val failureReason: String = "None"
    )
    val extensionStatuses = mutableStateListOf<ExtensionStatus>()

    // Legacy network metrics
    data class NetworkMetric(
        val url: String,
        val dnsMs: Long,
        val tlsMs: Long,
        val htmlMs: Long,
        val jsMs: Long,
        val totalMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    )
    val networkMetrics = mutableStateListOf<NetworkMetric>()

    // Monitor states
    data class DownloadMonitorState(
        val filename: String = "",
        val size: String = "",
        val threads: Int = 16,
        val activeChunks: Int = 12,
        val speed: String = "",
        val status: String = "Stopped"
    )
    val downloadMonitorState = mutableStateOf(DownloadMonitorState())

    data class PermissionConnectionState(
        val websiteRequestReceived: Boolean = false,
        val browserPermissionPromptShown: Boolean = false,
        val androidPermissionGranted: Boolean = false,
        val webViewGrantApplied: Boolean = false,
        val mediaStreamCreated: Boolean = false,
        val websiteActuallyWorking: Boolean = false
    )
    val permissionConnectionState = mutableStateOf(PermissionConnectionState())

    data class DesktopConnectionState(
        val userAgentApplied: Boolean = false,
        val viewportApplied: Boolean = false,
        val cssRulesApplied: Boolean = false,
        val hostRewriteApplied: Boolean = false,
        val hostRewriteSkipped: Boolean = true,
        val desktopPageLoaded: Boolean = false
    )
    val desktopConnectionState = mutableStateOf(DesktopConnectionState())

    data class DesktopModeMonitorState(
        val desktopModeEnabled: Boolean = false,
        val userAgentType: String = "Mobile",
        val viewportWidth: Int = 360,
        val urlRewriteSuccess: Boolean = true,
        val currentUrl: String = "orion://newtab"
    )
    val desktopModeMonitorState = mutableStateOf(DesktopModeMonitorState())

    data class VoiceEngineState(
        val wakeWordState: String = "Sleeping",
        val lastKeyword: String = "None",
        val recognitionStatus: String = "Idle",
        val detectedIntent: String = "None",
        val executionStatus: String = "Idle"
    )
    val voiceEngineState = mutableStateOf(VoiceEngineState())

    data class WebViewMonitorState(
        val currentPageUrl: String = "orion://newtab",
        val isJavaScriptEnabled: Boolean = true,
        val domReady: Boolean = false,
        val cookiesAllowed: Boolean = true,
        val domStorageAllowed: Boolean = true,
        val serviceWorkerState: String = "Stopped"
    )
    val webViewMonitorState = mutableStateOf(WebViewMonitorState())

    data class SessionMonitorState(
        val totalTabs: Int = 1,
        val frozenTabs: Int = 0,
        val activeTabs: Int = 1,
        val sessionRestoreStatus: String = "Success"
    )
    val sessionMonitorState = mutableStateOf(SessionMonitorState())

    data class Hardware资源MonitorState(
        val ramUsedMb: Int = 120,
        val cpuUsagePercent: Int = 5,
        val gpuUsagePercent: Int = 10,
        val activeWebViews: Int = 1
    )
    val hardwareResourceMonitorState = mutableStateOf(Hardware资源MonitorState())

    // 10. Robust Trace Checklist system of any pipeline failures
    data class TraceStep(
        val label: String,
        val status: Boolean
    )

    data class PipelineStep(
        val name: String,
        val className: String = "",
        val methodName: String = "",
        val callbackName: String = "",
        val status: String = "PASS", // "PASS", "FAIL", "SKIPPED"
        val executionTime: String = "2ms",
        val errorCode: String = "N/A",
        val reason: String = "Subsystem operating normally"
    )

    data class FailureTrace(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val timestamp: Long = System.currentTimeMillis(),
        val steps: List<TraceStep>,
        val reason: String,
        val suggestedFix: String,
        val classLocation: String,
        val callbackFired: String,
        val component: String,
        val blockedBy: String? = null, // Android, WebView, Website, Server, etc.
        val methodName: String = "execute",
        val executionTime: String = "12ms",
        val errorCode: String = "0x80004005",
        val status: String = "FAILED",
        val pipelineSteps: List<PipelineStep> = emptyList()
    )
    val failureTraces = mutableStateListOf<FailureTrace>()

    init {
        resetToDefaultDiagnostics()
    }

    fun initFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)
        _isDeveloperModeEnabled.value = prefs.getBoolean("developer_mode_enabled", false)
    }

    fun setDeveloperModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("orion_browser_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("developer_mode_enabled", enabled).apply()
        _isDeveloperModeEnabled.value = enabled
    }

    // Telemetry log functions with size capping to 100 entries
    fun logError(component: String, message: String, level: String = "ERROR", location: String) {
        if (errorLogs.size >= 100) errorLogs.removeAt(errorLogs.lastIndex)
        errorLogs.add(0, ErrorEvent(component = component, message = message, level = level, location = location))
    }

    fun logPermissionEvent(requester: String, permission: String, decision: String, layer: String) {
        if (permissionEvents.size >= 100) permissionEvents.removeAt(permissionEvents.lastIndex)
        permissionEvents.add(0, PermissionEvent(requester = requester, permission = permission, decision = decision, layer = layer))
    }

    fun logExtensionEvent(extensionId: String, extensionName: String, eventType: String, detail: String) {
        if (extensionEvents.size >= 100) extensionEvents.removeAt(extensionEvents.lastIndex)
        extensionEvents.add(0, ExtensionEvent(extensionId = extensionId, extensionName = extensionName, eventType = eventType, detail = detail))
    }

    fun logDownloadEvent(filename: String, bytesTransferred: Long, totalBytes: Long, eventType: String, speed: String) {
        if (downloadEvents.size >= 100) downloadEvents.removeAt(downloadEvents.lastIndex)
        downloadEvents.add(0, DownloadEvent(filename = filename, bytesTransferred = bytesTransferred, totalBytes = totalBytes, eventType = eventType, speed = speed))
    }

    fun logVoiceEvent(eventType: String, message: String, confidence: Float = 0.95f) {
        if (voiceEvents.size >= 100) voiceEvents.removeAt(voiceEvents.lastIndex)
        voiceEvents.add(0, VoiceEvent(eventType = eventType, message = message, confidence = confidence))
    }

    // Legacy permission logs
    fun logPermission(
        origin: String,
        permission: String,
        browserPerm: String,
        androidPerm: String,
        webViewPerm: String,
        mediaStreamStatus: String,
        failurePoint: String
    ) {
        permissionLogs.add(0, PermissionLog(
            origin = origin,
            permission = permission,
            browserPermission = browserPerm,
            androidPermission = androidPerm,
            webViewPermission = webViewPerm,
            mediaStreamStatus = mediaStreamStatus,
            failurePoint = failurePoint
        ))
    }

    // Legacy network timing logs
    fun logNetwork(url: String, dns: Long, tls: Long, html: Long, js: Long, total: Long) {
        networkMetrics.add(0, NetworkMetric(
            url = url,
            dnsMs = dns,
            tlsMs = tls,
            htmlMs = html,
            jsMs = js,
            totalMs = total
        ))
    }

    fun addFailureTrace(trace: FailureTrace) {
        failureTraces.add(0, trace)
    }

    // Interactive failure simulation engine
    fun triggerSampleFailureTrace(type: String) {
        if (type == "Microphone" || type == "Camera" || type == "Location" || type == "Android Restriction") {
            failureTraces.removeAll {
                it.component == "Microphone" || it.component == "Camera" || it.component == "Location" || it.component == "Android Restriction"
            }
        }
        when (type) {
            "Microphone" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Microphone Request Failed",
                        component = "Microphone",
                        steps = listOf(
                            TraceStep("Website Request", true),
                            TraceStep("Browser Permission", true),
                            TraceStep("Android Permission", true),
                            TraceStep("WebView Permission", true),
                            TraceStep("Media Stream", false)
                        ),
                        reason = "Audio Session Creation Failed (HAL reported busy)",
                        suggestedFix = "Disable other background applications accessing the mic, check Android Settings -> Apps -> Orion permissions, or verify if the Audio Session Controller hardware is offline.",
                        classLocation = "com.example.browser.OrionSpeechBridge",
                        callbackFired = "onPermissionRequest -> request.grant()",
                        blockedBy = "WebView Permission Bridge",
                        methodName = "onPermissionRequest",
                        executionTime = "45ms",
                        errorCode = "AUDIO_SESSION_CR_FAIL (0x12A)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep(
                                name = "Permission Engine",
                                className = "com.example.permissionengine.PermissionEngineImpl",
                                methodName = "getPermissionState",
                                callbackName = "onRequestStart",
                                status = "PASS",
                                executionTime = "2ms",
                                errorCode = "0x0",
                                reason = "Permission subsystem active & online"
                            ),
                            PipelineStep(
                                name = "WebChromeClient.onPermissionRequest()",
                                className = "com.example.browser.BrowserViewModel.OrionWebChromeClient",
                                methodName = "onPermissionRequest",
                                callbackName = "onPermissionRequest",
                                status = "PASS",
                                executionTime = "6ms",
                                errorCode = "0x0",
                                reason = "YouTube requested AUDIO_CAPTURE resources"
                            ),
                            PipelineStep(
                                name = "AndroidRuntimePermissionManager",
                                className = "com.example.browser.BrowserScreen",
                                methodName = "requestRecordAudio",
                                callbackName = "systemPermissionLauncher",
                                status = "PASS",
                                executionTime = "1250ms",
                                errorCode = "0x0",
                                reason = "System RECORD_AUDIO was already authorized/granted"
                            ),
                            PipelineStep(
                                name = "request.grant()",
                                className = "android.webkit.PermissionRequest",
                                methodName = "grant",
                                callbackName = "onPermissionGranted",
                                status = "PASS",
                                executionTime = "3ms",
                                errorCode = "0x0",
                                reason = "WebView successfully applied native resource grant"
                            ),
                            PipelineStep(
                                name = "WebViewPermissionBridge",
                                className = "com.example.permissionengine.WebViewPermissionBridge",
                                methodName = "handleWebViewPermissionRequest",
                                callbackName = "onBridgeResult",
                                status = "PASS",
                                executionTime = "1ms",
                                errorCode = "0x0",
                                reason = "State successfully synchronized to browser pool"
                            ),
                            PipelineStep(
                                name = "navigator.mediaDevices.getUserMedia()",
                                className = "chromium_bridge.js",
                                methodName = "getUserMedia",
                                callbackName = "Promise.reject()",
                                status = "FAIL",
                                executionTime = "45ms",
                                errorCode = "0x0002",
                                reason = "Audio Session Creation Failed"
                            ),
                            PipelineStep(
                                name = "Media Stream",
                                className = "com.example.browser.voiceengine.OrionVoiceEngine",
                                methodName = "AudioRecord.startRecording",
                                callbackName = "onError",
                                status = "SKIPPED",
                                executionTime = "0ms",
                                errorCode = "N/A",
                                reason = "Aborted due to preceding track allocation failure"
                            )
                        )
                    )
                )
                logError("Microphone", "getUserMedia() failure on youtube.com - Audio Session Creation Failed", "ERROR", "OrionSpeechBridge")
                logPermissionEvent("youtube.com", "Microphone", "FAIL", "WebView Bridge")
            }
            "Camera" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Camera Allocation Failed",
                        component = "Camera",
                        steps = listOf(
                            TraceStep("Website Request", true),
                            TraceStep("Browser Permission", true),
                            TraceStep("Android Permission", true),
                            TraceStep("Camera Open", false),
                            TraceStep("Preview Start", false)
                        ),
                        reason = "CameraManager.openCamera() raised exception: MAX_CAMERAS_IN_USE.",
                        suggestedFix = "Close any background apps currently capturing video or disable battery saver.",
                        classLocation = "com.example.browser.OrionVideoEngine",
                        callbackFired = "CameraDevice.StateCallback.onError()",
                        blockedBy = "Android OS Hardware Lock",
                        methodName = "openCamera",
                        executionTime = "220ms",
                        errorCode = "MAX_CONCURRENT_CAMERAS (0x03)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Permission Engine", "com.example.permissionengine.PermissionEngineImpl", "getPermissionState", "onRequestStart", "PASS", "2ms", "0x0", "Subsystem initialized"),
                            PipelineStep("WebChromeClient.onPermissionRequest()", "com.example.browser.BrowserViewModel.OrionWebChromeClient", "onPermissionRequest", "onPermissionRequest", "PASS", "4ms", "0x0", "Google Meet requested VIDEO_CAPTURE"),
                            PipelineStep("AndroidRuntimePermissionManager", "com.example.browser.BrowserScreen", "requestCamera", "systemPermissionLauncher", "PASS", "950ms", "0x0", "System CAMERA granted successfully"),
                            PipelineStep("request.grant()", "android.webkit.PermissionRequest", "grant", "onPermissionGranted", "PASS", "2ms", "0x0", "WebView granted video capture"),
                            PipelineStep("Camera open", "android.hardware.camera2.CameraManager", "openCamera", "CameraDevice.StateCallback.onError", "FAIL", "220ms", "MAX_CAMERAS_IN_USE (3)", "Resource allocation failed. Channel busy"),
                            PipelineStep("Preview Start", "android.view.Surface", "lockCanvas", "onFrameAvailable", "SKIPPED", "0ms", "N/A", "Aperture stream allocation aborted")
                        )
                    )
                )
                logError("Camera", "Resource allocation failed. Channel busy.", "ERROR", "OrionVideoEngine")
                logPermissionEvent("meet.google.com", "Camera", "DENIED", "Android OS")
            }
            "File Upload" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Upload Selector Dispatched Failed",
                        component = "File Upload",
                        steps = listOf(
                            TraceStep("Upload Click", true),
                            TraceStep("File Picker Open", true),
                            TraceStep("URI Returned", false),
                            TraceStep("Website Received File", false),
                            TraceStep("Upload Started", false)
                        ),
                        reason = "Activity result reported RESULT_CANCELED or security permission denial on the selected content provider URI.",
                        suggestedFix = "Verify read permission on system storage. Enable Android Storage Access Framework helper in Settings.",
                        classLocation = "com.example.browser.NativeBridge",
                        callbackFired = "WebChromeClient.onShowFileChooser()",
                        blockedBy = "Android Document Provider",
                        methodName = "onShowFileChooser",
                        executionTime = "5400ms",
                        errorCode = "RESULT_CANCELED (0)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Upload Click", "com.example.browser.NativeBridge", "setupUploadClick", "onUploadClick", "PASS", "12ms", "0x0", "DOM trigger capture successful"),
                            PipelineStep("File Picker Open", "android.webkit.WebChromeClient", "onShowFileChooser", "onShowFileChooser", "PASS", "15ms", "0x0", "Dispatched storage picker intent"),
                            PipelineStep("URI Returned", "com.example.browser.BrowserActivity", "onActivityResult", "onActivityResult", "FAIL", "5400ms", "RESULT_CANCELED (0)", "Activity result reported cancelled or permission denial on provider URI"),
                            PipelineStep("Website Received File", "android.webkit.ValueCallback", "onReceiveValue", "onReceiveValue", "SKIPPED", "0ms", "N/A", "Upload path aborted"),
                            PipelineStep("Upload Started", "com.example.browser.NativeBridge", "uploadFileStream", "onProgress", "SKIPPED", "0ms", "N/A", "Transit channel closed")
                        )
                    )
                )
                logError("File Upload", "Intent dispatch returned CANCELED state", "WARNING", "NativeBridge")
            }
            "Location" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "GPS Geolocation Access Blocked",
                        component = "Location",
                        steps = listOf(
                            TraceStep("Geolocation Request", true),
                            TraceStep("Browser Permission", true),
                            TraceStep("Android Permission", true),
                            TraceStep("GPS Access", false),
                            TraceStep("Location Callback", false)
                        ),
                        reason = "FusedLocationProviderClient timed out after 10000ms. No satellite lock acquired (Indoor signal loss).",
                        suggestedFix = "Move to an outdoor location, verify GPS is turned ON in system quick settings, or restart Android Location Service.",
                        classLocation = "com.example.browser.OrionSitePermissionManager",
                        callbackFired = "LocationCallback.onLocationResult(null)",
                        blockedBy = "Android GPS Module",
                        methodName = "requestLocationUpdates",
                        executionTime = "10000ms",
                        errorCode = "GPS_TIMEOUT (0x1F)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Geolocation Request", "com.example.browser.OrionSitePermissionManager", "requestLocation", "onRequest", "PASS", "1ms", "0x0", "Origin Maps requested coords"),
                            PipelineStep("Browser Permission", "com.example.permissionengine.PermissionEngineImpl", "getPermissionState", "onCheck", "PASS", "3ms", "0x0", "Website allowed by policy"),
                            PipelineStep("Android Permission", "com.example.browser.BrowserScreen", "requestLocation", "systemPermissionLauncher", "PASS", "88ms", "0x0", "ACCESS_FINE_LOCATION was already GRANTED"),
                            PipelineStep("GPS Access", "com.google.android.gms.location.FusedLocationProviderClient", "requestLocationUpdates", "LocationCallback.onLocationResult", "FAIL", "10000ms", "ERROR_TIMEOUT (0x1F)", "FusedLocationProvider timed out. No satellite lock acquired"),
                            PipelineStep("Location Callback", "android.location.LocationListener", "onLocationChanged", "onLocationChanged", "SKIPPED", "0ms", "N/A", "Bypassed callback due to lock timeout")
                        )
                    )
                )
                logError("Geolocation", "FusedLocationProvider timed out", "ERROR", "OrionSitePermissionManager")
                logPermissionEvent("maps.google.com", "Location", "DENIED", "Android OS")
            }
            "Notification" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Push Notification Connection Aborted",
                        component = "Notification",
                        steps = listOf(
                            TraceStep("Notification Request", true),
                            TraceStep("Browser Permission", true),
                            TraceStep("Android Permission", true),
                            TraceStep("Notification Registration", false),
                            TraceStep("Delivery", false)
                        ),
                        reason = "Registration declined: Push Not Allowed. Client blocked notifications explicitly for domain.",
                        suggestedFix = "Unlock notification toggles for this website inside site info settings panel.",
                        classLocation = "com.example.browser.BackgroundAnalyzer",
                        callbackFired = "onRegisterPushNotification -> reject()",
                        blockedBy = "WebView Security Sandbox",
                        methodName = "requestPushPermission",
                        executionTime = "5ms",
                        errorCode = "BLOCKED_BY_USER_POLICY",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Notification Request", "com.example.browser.BackgroundAnalyzer", "requestPushPermission", "onPrompt", "PASS", "4ms", "0x0", "FCM push registration started"),
                            PipelineStep("Browser Permission", "com.example.browser.BrowserViewModel", "getNotificationStatus", "onCheck", "PASS", "1ms", "0x0", "Domain blacklisted or denied under site info dialog"),
                            PipelineStep("Android Permission", "android.app.NotificationManager", "areNotificationsEnabled", "areNotificationsEnabled", "FAIL", "5ms", "DENIED", "Notification channel blocked by user preference or system setting"),
                            PipelineStep("Notification Registration", "com.google.firebase.messaging.FirebaseMessaging", "getToken", "onFailure", "SKIPPED", "0ms", "N/A", "Service registration aborted"),
                            PipelineStep("Delivery", "com.example.browser.BackgroundAnalyzer", "showWebNotification", "onShow", "SKIPPED", "0ms", "N/A", "UI feedback suppressed")
                        )
                    )
                )
                logError("Notifications", "Registration rejected by user domain blacklist", "ERROR", "BackgroundAnalyzer")
                logPermissionEvent("facebook.com", "Notification", "DENIED", "Browser UI")
            }
            "Extension" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Content Script Injection Blocked",
                        component = "Extension",
                        steps = listOf(
                            TraceStep("Manifest Loaded", true),
                            TraceStep("Permissions Loaded", true),
                            TraceStep("Content Script Injected", false),
                            TraceStep("Runtime API Loaded", false),
                            TraceStep("DOM Modified", false)
                        ),
                        reason = "Content Security Policy (CSP) headers mismatch. Evaluation of inline scripts blocked on domain.",
                        suggestedFix = "Bypass or modify CSP headers via proxy intercept rules or configure script execution to use isolated contexts.",
                        classLocation = "com.example.extensionengine.ExtensionManager",
                        callbackFired = "onPageCommitVisible -> injectScript()",
                        blockedBy = "Website CSP Header Policy",
                        methodName = "injectScript",
                        executionTime = "38ms",
                        errorCode = "ERR_CSP_BLOCK (0x5C)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Manifest Loaded", "com.example.extensionengine.ExtensionManager", "loadManifest", "onManifestLoaded", "PASS", "5ms", "0x0", "Parsed extension manifest schema"),
                            PipelineStep("Permissions Loaded", "com.example.extensionengine.ExtensionManager", "checkPermissions", "onPermissionsReady", "PASS", "2ms", "0x0", "Authorized cross-origin web access"),
                            PipelineStep("Content Script Injected", "android.webkit.WebView", "evaluateJavascript", "onPageCommitVisible", "FAIL", "38ms", "ERR_CSP_VIOLATION", "Inline Script execution blocked by domain Content-Security-Policy header rules"),
                            PipelineStep("Runtime API Loaded", "com.example.extensionengine.ExtensionManager", "bindApi", "onApiBound", "SKIPPED", "0ms", "N/A", "Registration skipped"),
                            PipelineStep("DOM Modified", "com.example.extensionengine.ExtensionManager", "modifyDom", "onComplete", "SKIPPED", "0ms", "N/A", "DOM mutation skipped")
                        )
                    )
                )
                logError("Extension", "CSP Policy blocked Inline Script injection", "ERROR", "ExtensionManager")
                logExtensionEvent("ext_grok_4", "Grok-4 Helper", "FAILED", "Failed injection due to CSP lock")
            }
            "Desktop Mode" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Desktop Layout Malfunction",
                        component = "Desktop Mode",
                        steps = listOf(
                            TraceStep("Desktop Mode Enabled", true),
                            TraceStep("Desktop User Agent Applied", true),
                            TraceStep("Viewport Modified", true),
                            TraceStep("Desktop URL Rewrite", false),
                            TraceStep("Desktop Layout Loaded", false)
                        ),
                        reason = "Website hard-coded CSS stylesheets ignored viewport constraints and forced mobile layout overlay.",
                        suggestedFix = "Enable responsive zoom scale force override-rules inside Desktop options.",
                        classLocation = "com.example.browser.DesktopModeManager",
                        callbackFired = "onLoadFinished -> evalViewportScript()",
                        blockedBy = "Website CSS Overrides",
                        methodName = "evalViewportScript",
                        executionTime = "85ms",
                        errorCode = "ERR_VIEWPORT_OVERRIDE",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Desktop Mode Active", "com.example.browser.DesktopModeManager", "toggleDesktopMode", "onChanged", "PASS", "1ms", "0x0", "Desktop state enabled globally"),
                            PipelineStep("Desktop User Agent", "android.webkit.WebSettings", "setUserAgentString", "onPageLoad", "PASS", "2ms", "0x0", "Applied Chrome-Desktop UA payload"),
                            PipelineStep("Viewport Modified", "android.webkit.WebView", "evaluateJavascript", "onPageLoadStarted", "PASS", "12ms", "0x0", "Injected meta viewport viewport-width overrides"),
                            PipelineStep("Desktop URL Rewrite", "com.example.browser.DesktopModeManager", "rewriteMobileUrl", "onBeforeRequest", "FAIL", "85ms", "SKIP_NO_RULES", "Target domain does not host mobile subdomains, fallback to desktop-viewport CSS override"),
                            PipelineStep("Desktop Layout Loaded", "android.webkit.WebView", "loadUrl", "onPageFinished", "FAIL", "250ms", "OVERFLOW_MOBILE_CSS", "Media Query overridden on host by hardcoded CSS mobile wrapper stylesheet elements")
                        )
                    )
                )
                logError("DesktopMode", "Hardcoded CSS locked mobile layout", "WARNING", "DesktopModeManager")
            }
            "Download" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Multi-threaded Download Chunk Aborted",
                        component = "Download",
                        steps = listOf(
                            TraceStep("Connection Open", true),
                            TraceStep("Range Request", true),
                            TraceStep("Chunk Download", false),
                            TraceStep("Merge", false),
                            TraceStep("Save", false)
                        ),
                        reason = "Parallel Socket Connection Reset by TCP Peer. Stream closed prematurely by host.",
                        suggestedFix = "Reduce parallel thread chunk config from 16 down to 4, or trigger resumed key manually.",
                        classLocation = "com.example.downloadengine.ChunkDownloader",
                        callbackFired = "onConnectionError -> retryChunkWithOffset()",
                        blockedBy = "Server Range Request Restrict",
                        methodName = "retryChunkWithOffset",
                        executionTime = "850ms",
                        errorCode = "ECONNRESET (104)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Connection Open", "com.example.downloadengine.ChunkDownloader", "createConnection", "onConnected", "PASS", "45ms", "0x0", "TCP Connection established secure channel"),
                            PipelineStep("Range Request", "com.example.downloadengine.ChunkDownloader", "sendHeaders", "onHeadersReceived", "PASS", "35ms", "0x0", "Server accepted range chunk byte ranges"),
                            PipelineStep("Chunk Download", "com.example.downloadengine.ChunkDownloader", "downloadChunk", "onConnectionError", "FAIL", "850ms", "ECONNRESET", "Parallel Socket Connection Reset by TCP peer. Chunk 12 timed out"),
                            PipelineStep("Merge", "com.example.downloadengine.ChunkDownloader", "mergeChunks", "onMergeFinished", "SKIPPED", "0ms", "N/A", "Aborted due to bad chunktrail payload"),
                            PipelineStep("Save", "com.example.downloadengine.ChunkDownloader", "writeToDisk", "onFinished", "SKIPPED", "0ms", "N/A", "Payload flush bypassed")
                        )
                    )
                )
                logError("Download", "TCP socket reset chunk index 12", "ERROR", "ChunkDownloader")
                logDownloadEvent("movie.mp4", 450000000L, 2400000000L, "ERROR", "0 MB/s")
            }
            "Voice Engine" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Voice Command NLP Mapping Blocked",
                        component = "Voice Engine",
                        steps = listOf(
                            TraceStep("Wake Word Detection", true),
                            TraceStep("Speech Recognition", true),
                            TraceStep("Intent Parsing", false),
                            TraceStep("Action Router", false),
                            TraceStep("Execution", false)
                        ),
                        reason = "NLP engine failed to parse speech tokens to any registered intent command in Voice Database.",
                        suggestedFix = "Simplify command phrasing, calibrate wake microphone threshold, or speak closer.",
                        classLocation = "com.example.browser.voiceengine.CommandRouter",
                        callbackFired = "onRecognizerResult -> dispatchIntent()",
                        blockedBy = "NLP Lexicon Decoder",
                        methodName = "dispatchIntent",
                        executionTime = "450ms",
                        errorCode = "ERR_NO_MATCH (0x0F)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Wake Word Detection", "com.example.browser.voiceengine.OrionHotwordEngine", "startEngine", "onKeywordDetected", "PASS", "12ms", "0x0", "Keyword 'Hello Orion' matches threshold"),
                            PipelineStep("Speech Recognition", "com.example.browser.voiceengine.SpeechRecognitionEngine", "startListening", "onResultsReady", "PASS", "3500ms", "0x0", "Tokens parsed to string: 'Open YouTube'"),
                            PipelineStep("Intent Parsing", "com.example.browser.voiceengine.IntentEngine", "parseIntent", "onIntentParsed", "FAIL", "450ms", "ERR_NO_MATCH", "NLP tokenizer failed to map tokens to any registered database intent"),
                            PipelineStep("Action Router", "com.example.browser.voiceengine.BrowserActionEngine", "executeAction", "onActionExecuted", "SKIPPED", "0ms", "N/A", "Command aborted"),
                            PipelineStep("Execution", "com.example.browser.voiceengine.AssistantActivationManager", "dispatch", "onFinish", "SKIPPED", "0ms", "N/A", "Task queue cleared")
                        )
                    )
                )
                logError("VoiceEngine", "NLP tokenizer failed command mapping", "WARNING", "CommandRouter")
                logVoiceEvent("ERROR", "Command unrecognized: Open YouTube", 0.42f)
            }
            "Android Restriction" -> {
                addFailureTrace(
                    FailureTrace(
                        title = "Android OS Background Isolation Restriction",
                        component = "Android Restriction",
                        steps = listOf(
                            TraceStep("Orion Action Initiated", true),
                            TraceStep("Application Active Lifecycle", true),
                            TraceStep("System Service Request Broadcasted", true),
                            TraceStep("Hardware Background Allocation", false)
                        ),
                        reason = "Android Battery Optimization strictly blocks the native WebView task loop from keeping websocket stream alive.",
                        suggestedFix = "Bypass device sleep boundaries. Disable SwiftBrowser from Battery Optimizations under Android Settings -> Apps.",
                        classLocation = "com.example.browser.MemoryLeakDetector",
                        callbackFired = "onTrimMemory -> releaseResources()",
                        blockedBy = "Android Power Management Policy",
                        methodName = "releaseResources",
                        executionTime = "12ms",
                        errorCode = "TRIM_MEMORY_RUNNING_CRITICAL (80)",
                        status = "FAILED",
                        pipelineSteps = listOf(
                            PipelineStep("Orion Action Initiated", "com.example.browser.BrowserViewModel", "startAction", "onTrigger", "PASS", "1ms", "0x0", "User triggered background stream monitor"),
                            PipelineStep("Application Active Lifecycle", "com.example.browser.BrowserActivity", "onPause", "onStop", "PASS", "10ms", "0x0", "Orion shifted to background task queue"),
                            PipelineStep("System Service Request Broadcasted", "android.app.job.JobScheduler", "schedule", "onStartJob", "PASS", "8ms", "0x0", "Scheduled background task executor service"),
                            PipelineStep("Hardware Background Allocation", "com.example.browser.MemoryLeakDetector", "onTrimMemory", "onTrimMemory", "FAIL", "12ms", "TRIM_CRITICAL", "Android background lock released thread allocations due to steep battery optimization policy limits")
                        )
                    )
                )
                logError("SystemRestrict", "Activity lifecycle trimmed background task limits", "ERROR", "MemoryLeakDetector")
            }
        }
    }

    fun resetToDefaultDiagnostics() {
        permissionLogs.clear()
        extensionStatuses.clear()
        networkMetrics.clear()
        failureTraces.clear()
        errorLogs.clear()
        permissionEvents.clear()
        extensionEvents.clear()
        downloadEvents.clear()
        voiceEvents.clear()

        // 1. Populate Errors sliding window
        logError("Network", "DNS Resolution timeout on maps.google.com", "ERROR", "NetworkOptimizer")
        logError("Extension", "DOM Inject Blocked on GitHub overlay", "WARNING", "ExtensionManager")
        logError("SpeechEngine", "Low confidence voice parsing (0.42)", "WARNING", "SpeechRecognitionManager")
        logError("Database", "Room migration context mismatch handled", "WARNING", "AICacheDatabase")
        logError("WebView", "Hardware compilation cache overflow flushed", "WARNING", "WebViewPool")
        logError("Security", "Mixed Content Blocked: insecure image on blog spot", "ERROR", "NativeBridge")
        logError("Media", "AudioTrack output underflow during voice playback", "WARNING", "NativeAudioEngine")
        logError("Download", "Disk buffer write slow down, reducing download sockets", "WARNING", "ChunkDownloader")

        // 2. Populate Permissions sliding window
        logPermissionEvent("youtube.com", "Microphone", "GRANTED", "WebView Bridge")
        logPermissionEvent("meet.google.com", "Camera", "GRANTED", "WebView Bridge")
        logPermissionEvent("maps.google.com", "Location", "DENIED", "Android OS")
        logPermissionEvent("github.com", "Notification", "PROMPTED", "Browser UI")
        logPermissionEvent("gmail.com", "Microphone", "GRANTED", "Android OS")
        logPermissionEvent("drive.google.com", "Storage Access", "GRANTED", "Android OS")

        // 3. Populate Extensions sliding window
        logExtensionEvent("ext_dark_reader", "Dark Reader", "LOADED", "Manifest parsed and files registered")
        logExtensionEvent("ext_dark_reader", "Dark Reader", "INJECTED", "Content script evaluated on active tab")
        logExtensionEvent("ext_dark_reader", "Dark Reader", "DOM_MODIFY", "CSS dark style node successfully inserted")
        logExtensionEvent("ext_adblock", "AdShield Block", "LOADED", "Adblock easylist rules database compiled in-memory")
        logExtensionEvent("ext_adblock", "AdShield Block", "API_CALL", "Intercepted XMLRequest on tracker domain")
        logExtensionEvent("ext_grok_4", "Grok-4 Helper", "LOADED", "Grok companion loaded")
        logExtensionEvent("ext_grok_4", "Grok-4 Helper", "FAILED", "Failed injection due to CSP lock")

        // 4. Populate Download Events sliding window
        logDownloadEvent("movie.mp4", 0L, 2400000000L, "START", "0 MB/s")
        logDownloadEvent("movie.mp4", 512000000L, 2400000000L, "CHUNK_COMPLETE", "18 MB/s")
        logDownloadEvent("movie.mp4", 1200000000L, 2400000000L, "CHUNK_COMPLETE", "15 MB/s")
        logDownloadEvent("document.pdf", 0L, 4500000L, "START", "2 MB/s")
        logDownloadEvent("document.pdf", 4500000L, 4500000L, "SUCCESS", "4.5 MB/s")

        // 5. Populate Voice Events sliding window
        logVoiceEvent("WAKE_WORD", "Keyword 'Hello Orion' matches threshold", 0.98f)
        logVoiceEvent("RECOGNITION", "Recognized voice query: Open YouTube", 0.94f)
        logVoiceEvent("INTENT_PARSE", "Voice query matched action route: NAVIGATE", 0.91f)
        logVoiceEvent("ROUTING", "Intent routing successful, launching youtube.com", 0.99f)

        // Legacy compatibility logs
        logPermission("youtube.com", "Microphone", "GRANTED", "GRANTED", "GRANTED", "SUCCESS", "None")
        logPermission("meet.google.com", "Camera", "GRANTED", "GRANTED", "GRANTED", "FAILED", "Audio Session Creation Failed")
        logPermission("maps.google.com", "Location", "GRANTED", "DENIED", "FALLBACK", "FAILED", "getUserMedia()")

        extensionStatuses.add(ExtensionStatus("ext_dark_reader", "Dark Reader"))
        extensionStatuses.add(ExtensionStatus("ext_adblock", "AdShield Block", runtimeApiStatus = "Active"))
        extensionStatuses.add(ExtensionStatus("ext_grok_4", "Grok-4 Helper", contentScriptInjected = "Failed", failureReason = "Content Script Injection Failed"))

        logNetwork("youtube.com", 12, 40, 120, 340, 512)
        logNetwork("google.com", 8, 24, 75, 125, 232)
        logNetwork("github.com", 25, 60, 180, 520, 785)

        downloadMonitorState.value = DownloadMonitorState(
            filename = "movie.mp4",
            size = "2.4 GB",
            threads = 16,
            activeChunks = 12,
            speed = "18 MB/s",
            status = "Running"
        )

        voiceEngineState.value = VoiceEngineState(
            wakeWordState = "Listening",
            lastKeyword = "Hello Orion",
            recognitionStatus = "Success",
            detectedIntent = "Open YouTube",
            executionStatus = "Success"
        )

        // Automatically trigger Microphone trace as default
        triggerSampleFailureTrace("Microphone")
    }

    fun getAndroidRestrictionReason(context: Context): String? {
        try {
            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                return "runtime permission missing"
            }
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                return "battery optimization interference"
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                if (audioManager.mode == android.media.AudioManager.MODE_IN_COMMUNICATION || audioManager.mode == android.media.AudioManager.MODE_IN_CALL) {
                    return "audio hardware busy"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
