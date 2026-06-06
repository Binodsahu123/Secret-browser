package com.example.extensionengine

import android.content.Context
import android.webkit.WebView
import java.io.File

class ContentScriptManager(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val scriptInjector: ScriptInjector,
    private val cssInjector: CssInjector
) {

    /**
     * Checks matching patterns and triggers evaluation of assets on current URL.
     */
    fun matchAndInject(webView: WebView, url: String, parsedExtensions: List<ParsedExtension>) {
        if (url.startsWith("about:") || url.startsWith("orion:") || url.startsWith("file://")) return

        for (ext in parsedExtensions) {
            for (spec in ext.contentScripts) {
                if (permissionManager.hasHostPermission(spec.matches, emptyList(), url)) {
                    val extensionDir = ExtensionDirectoryResolver.getExtensionDir(context, ext.id, ext.name)
                    
                    // 1. CSS Injections
                    for (cssPath in spec.css) {
                        try {
                            val cssContent = readExtensionFile(extensionDir, cssPath)
                            if (cssContent.isNotBlank()) {
                                cssInjector.injectCss(webView, cssContent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 2. JS Injections
                    for (jsPath in spec.js) {
                        try {
                            val jsContent = readExtensionFile(extensionDir, jsPath)
                            if (jsContent.isNotBlank()) {
                                val selfContainedScopedScript = """
                                    (function() {
                                        try {
                                            $jsContent
                                        } catch (e) {
                                            console.error("Content Script Exec Error in $jsPath: ", e);
                                        }
                                    })();
                                """.trimIndent()
                                scriptInjector.injectScript(webView, selfContainedScopedScript)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun readExtensionFile(extensionDir: File, relativePath: String): String {
        val cleanPath = relativePath.removePrefix("./").removePrefix("/")
        val file = File(extensionDir, cleanPath)
        if (file.exists()) {
            return file.readText()
        }
        val filenameOnly = relativePath.substringAfterLast("/")
        val fallbackFile = File(extensionDir, filenameOnly)
        if (fallbackFile.exists()) {
            return fallbackFile.readText()
        }
        return ""
    }
}
