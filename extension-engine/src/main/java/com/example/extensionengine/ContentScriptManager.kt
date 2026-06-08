package com.example.extensionengine

import android.content.Context
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
    fun matchAndInject(evaluator: ScriptEvaluator, url: String, parsedExtensions: List<ParsedExtension>) {
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
                                val cssKey = "style_${ext.id}_${cssPath.hashCode()}"
                                val guardAndInjectScript = """
                                    (function() {
                                        window._orionStylesInjected = window._orionStylesInjected || {};
                                        if (window._orionStylesInjected["$cssKey"]) return;
                                        window._orionStylesInjected["$cssKey"] = true;
                                        try {
                                             const style = document.createElement('style');
                                             style.type = 'text/css';
                                             style.innerHTML = ${org.json.JSONObject.quote(cssContent)};
                                             (document.head || document.documentElement).appendChild(style);
                                        } catch(e) {
                                             console.error("CSS Injection Error in $cssPath: ", e);
                                        }
                                    })();
                                """.trimIndent()
                                scriptInjector.injectScript(evaluator, guardAndInjectScript)
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
                                val jsKey = "script_${ext.id}_${jsPath.hashCode()}"
                                val selfContainedScopedScript = """
                                    (function() {
                                        window._orionScriptsInjected = window._orionScriptsInjected || {};
                                        if (window._orionScriptsInjected["$jsKey"]) return;
                                        window._orionScriptsInjected["$jsKey"] = true;
                                        try {
                                            const browser = window._orionGetExtensionContext("${ext.id}");
                                            const chrome = browser;
                                            $jsContent
                                        } catch (e) {
                                            console.error("Content Script Exec Error in $jsPath: ", e);
                                        }
                                    })();
                                """.trimIndent()
                                scriptInjector.injectScript(evaluator, selfContainedScopedScript)
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
