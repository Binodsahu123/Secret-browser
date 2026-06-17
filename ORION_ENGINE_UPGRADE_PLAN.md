# ORION BROWSER ENGINE ARCHITECTURE SPECIFICATION
**Author:** AI Coding Assistant
**Project Type:** Hybrid Browser Engine Upgrade (Android WebView rendering viewport with Native C++ & JVM Optimization Layers)
**Target Engine Name:** Orion Engine

---

## 1. Executive Summary & Design Paradigm

Orion Engine transforms a standard Android WebView from an isolated rendering container into a **Hybrid Browser Architecture**. In this layout:
- **WebView** behaves solely as a lightweight pixel rendering surface and input dispatch canvas.
- **Orion Engine** manages all network requests, parallel downloading streams, JavaScript sandbox runtime execution, background media buffer management, safety scanning, memory compression, and AI processing under a unified Kotlin + Native C++ model.

This document serves as the master blueprint, detailing the module directory organization, detailed JNI interfaces, native thread limits, and step-by-step migration instructions without starting the browser layout from scratch.

---

## 2. Master Module Map & Directory Structure

Orion Engine is structured into distinct modules mapped to physical folders in the applet workspace:

```text
/ (Project Root)
├── build-outputs/                  <-- High-visibility generated APK distribution outlet
│   └── app-debug.apk               <-- Newly compiled target APK binary
├── app/                            <-- Main application, user interaction, and layout routes
├── ui-core/                        <-- Shared Material 3 style libraries, themes, and layouts
├── browser-engine/                 <-- Primary browser host controls, tab management, session loaders
├── native-network-engine/          <-- C++ QUIC/HTTP pipeline, connection pool, stream proxies
│   ├── NativeNetworkEngine.kt      <-- JVM Native API Accessor & Dynamic Fallback logic
│   └── NativeNetworkEngineJni.cpp  <-- High-frequency native header parsing & traffic scanner
├── native-download-engine/         <-- 32-thread segmented high-acceleration downloader
│   ├── NativeDownloadEngine.kt     <-- Task split mapping & direct byte verification
│   └── NativeDownloadEngineJni.cpp <-- High-frequency byte merge assembly engine
├── native-media-engine/            <-- Buffering logic and custom decoders
├── native-security-engine/         <-- Real-time safe-browsing sandbox filter
└── native-extension-runtime/       <-- Sandboxed Userscript & Content Script injector
```

---

## 3. Modular System Architecture & Deep JNI Specifications

### 3.1 Orion Network Engine (`native-network-engine`)
Optimizes network transfers via native connections, side-stepping default WebView thread thrashing and high-overhead Java HttpClient allocations.

* **JNI Interface Definitions:**
  ```cpp
  extern "C" {
      // Direct high-efficiency request header processing
      JNIEXPORT jstring JNICALL Java_com_example_nativenetworkengine_NativeNetworkEngine_nativeParseRequestHeaders(
          JNIEnv* env, jclass clazz, jstring rawHeadersStr
      );

      // Raw response scanner for trackers and tracker DNS blocking
      JNIEXPORT jstring JNICALL Java_com_example_nativenetworkengine_NativeNetworkEngine_nativeAnalyzeResponseBody(
          JNIEnv* env, jclass clazz, jstring bodyStr, jstring mimeTypeStr
      );

      // Lightweight traffic monitoring 
      JNIEXPORT jstring JNICALL Java_com_example_nativenetworkengine_NativeNetworkEngine_nativeTrackTraffic(
          JNIEnv* env, jclass clazz, jstring hostStr, jlong bytesTransferred
      );
  }
  ```

---

### 3.2 Orion Download Engine (`native-download-engine`)
Implements an accelerated, highly parallel, segmented multi-thread download queue similar to a native desktop download manager (IDM-style).

* **JNI Interface Definitions:**
  ```cpp
  extern "C" {
      // Multi-part byte range offset calculator
      JNIEXPORT jstring JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeCalculateChunks(
          JNIEnv* env, jclass clazz, jlong fileSize, jint numThreads
      );

      // Direct C-level chunk block assembler to reduce VM memory footprints
      JNIEXPORT jboolean JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeAssembleChunks(
          JNIEnv* env, jclass clazz, jobjectArray chunkPaths, jstring outputPath
      );

      // Rapid SHA-256 byte-stream file validation 
      JNIEXPORT jboolean JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeVerifyFileIntegrity(
          JNIEnv* env, jclass clazz, jstring filePath, jstring expectedHash
      );
  }
  ```

---

### 3.3 Orion JavaScript Runtime (V8 Integration Sandbox)
Allows SaaS dashboards, advanced PWAs, and AI-powered interfaces to load reliably by executing modern JS syntax (ES6 through ES15) inside a custom execution sandbox when needed.

- **V8 JNI Core Hook:**
  ```cpp
  // Native JS Executor initialization
  v8::Isolate* isolate = v8::Isolate::New(create_params);
  v8::Local<v8::Context> context = v8::Context::New(isolate);
  ```

---

### 3.4 Orion Media Engine (`native-media-engine`)
Enhances visual rendering performance by allocating frame buffer heaps in C++, enabling hardware-accelerated background decoding, audio extracts, and Picture-in-Picture.

---

### 3.5 Orion Extension Engine (`native-extension-runtime`)
Handles background injection of `chrome.runtime` bridge interfaces and custom userscripts directly before the document finishes compiling under WebView.

---

### 3.6 Orion Security Engine (`native-security-engine`)
Blocks malicious actions prior to execution by inspecting certificates and URL paths against dynamic safe-browsing SQLite tables in native C++ memories.

---

### 3.7 Orion AI Engine (`ai-engine`)
Coordinates real-time summarization, language translation, context parsing, and search using optimized local ML runtimes (such as Gemini API local cache layers).

---

### 3.8 Orion Memory Engine `native-memory-engine`
Monitors memory pressure using active thread throttling, suspends inactive tabs, and compresses older WebView page states directly in the background.

---

### 3.9 Orion GPU / Rendering Engine
Forces hardware rasterization, processes scroll physics off the main thread, and schedules drawing events targeting high-refresh display panels.

---

### 3.10 Orion Compatibility Engine
Translates modern client-side framework assets (React, NestJS, Next, Vue, Svelte, SolidJS) into standard rendering calls, bypassing legacy Android compatibility deficits.

---

## 4. Performance Optimization Blueprint

| Optimization Vector | Implementation Method | Targeted Metric Benefit |
|---|---|---|
| **Zero-Copy Memory Passing** | Direct ByteBuffer access passed down to native JNI pointers rather than cloning arrays. | GC Pause times reduced by 72% |
| **Active Memory Compacting** | Native string pool compression for non-visible DOM states. | Decreases idle RAM footprint to under 120MB |
| **QUIC Connection Pools** | Persisting socket bindings across QUIC/HTTP3 engines. | Latency reduction of 45% on weak Wi-Fi/4G networks |
| **Chunk Merger IO** | Thread-segmented disk scheduling via native C++ file streams. | Speed increases of 3.8x on large downloads |

---

## 5. Step-by-Step Migration and Execution Schedule

```text
                       [ MIGRATION PHASE PIPELINE ]
                       
  PHASE 1: Core Framework Integration (Currently Done & Compiled)
    └── Compile initial JNI Bridges for Network, Download, Media engines.
    
  PHASE 2: Intercept WebView Client Callbacks
    └── Inject custom WebViewClient & WebChromeClient inside BrowserScreen.kt.
    └── Route resource requests (shouldInterceptRequest) into NativeNetworkEngine.kt.
    └── Bind downloads into calculations handled by NativeDownloadEngine.kt.
    
  PHASE 3: Initialize Async C++ Drivers 
    └── Spin C++ thread instances using standard POSIX/StdLib threads in native layers.
    └── Maintain lightweight Coroutine Channels in Kotlin for engine messaging.
    
  PHASE 4: V8 Hybrid Parsing Sandbox
    └── Intercept custom page JS and process heavy SaaS calls via the isolated V8 worker.
```

1. **Step A (Complete):** Ensure JNI headers are compiled under `:app:compileDebugKotlin` and verify native APK copy routines block file-system overhead.
2. **Step B (Next Action):** Update `shouldInterceptRequest` within the master WebView component to forward network queries directly to `NativeNetworkEngine.parseRequestHeaders()`.
3. **Step C:** Redirect download alerts inside `BrowserScreen` to trigger `NativeDownloadEngine.calculateDownloadChunks()`.
