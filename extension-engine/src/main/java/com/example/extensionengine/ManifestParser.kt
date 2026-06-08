package com.example.extensionengine

import org.json.JSONObject
import java.security.MessageDigest

data class ContentScriptSpec(
    val matches: List<String>,
    val js: List<String>,
    val css: List<String>
)

data class ParsedExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val manifestVersion: Int,
    val permissions: List<String>,
    val hostPermissions: List<String>,
    val backgroundScripts: List<String>,
    val isServiceWorker: Boolean,
    val contentScripts: List<ContentScriptSpec>,
    val actionPopup: String,
    val optionsPage: String,
    val manifestJson: String,
    val shortName: String = "",
    val iconPath: String = "",
    val installPath: String = "",
    val popupPath: String = "",
    val manifestPath: String = "",
    val backgroundPath: String = "",
    val isEnabled: Boolean = true
)

class ManifestParser {

    fun parse(manifestJsonStr: String): ParsedExtension {
        val root = JSONObject(manifestJsonStr)
        val name = root.optString("name", "Unnamed Extension")
        val version = root.optString("version", "1.0")
        val description = root.optString("description", "")
        val manifestVersion = root.optInt("manifest_version", 2)

        // Predictable hashing based on name to generate fixed extension IDs
        val id = generateExtensionId(name)

        // Parse permissions
        val permissions = mutableListOf<String>()
        val permissionsArray = root.optJSONArray("permissions")
        if (permissionsArray != null) {
            for (i in 0 until permissionsArray.length()) {
                permissions.add(permissionsArray.getString(i))
            }
        }

        // Parse host permissions (separated in MV3)
        val hostPermissions = mutableListOf<String>()
        val hostPermArray = root.optJSONArray("host_permissions")
        if (hostPermArray != null) {
            for (i in 0 until hostPermArray.length()) {
                hostPermissions.add(hostPermArray.getString(i))
            }
        }

        // Background scripts / Service workers
        val backgroundScripts = mutableListOf<String>()
        var isServiceWorker = false

        val backgroundObj = root.optJSONObject("background")
        if (backgroundObj != null) {
            // MV2 standard scripts
            val scriptsArray = backgroundObj.optJSONArray("scripts")
            if (scriptsArray != null) {
                for (i in 0 until scriptsArray.length()) {
                    backgroundScripts.add(scriptsArray.getString(i))
                }
            } else {
                // MV3 background service worker
                val serviceWorker = backgroundObj.optString("service_worker", "")
                if (serviceWorker.isNotBlank()) {
                    backgroundScripts.add(serviceWorker)
                    isServiceWorker = true
                }
            }
        }

        // Content scripts
        val contentScripts = mutableListOf<ContentScriptSpec>()
        val contentScriptsArray = root.optJSONArray("content_scripts")
        if (contentScriptsArray != null) {
            for (i in 0 until contentScriptsArray.length()) {
                val scriptObj = contentScriptsArray.getJSONObject(i)
                val matches = mutableListOf<String>()
                val matchesArray = scriptObj.optJSONArray("matches")
                if (matchesArray != null) {
                    for (j in 0 until matchesArray.length()) {
                        matches.add(matchesArray.getString(j))
                    }
                }

                val js = mutableListOf<String>()
                val jsArray = scriptObj.optJSONArray("js")
                if (jsArray != null) {
                    for (j in 0 until jsArray.length()) {
                        js.add(jsArray.getString(j))
                    }
                }

                val css = mutableListOf<String>()
                val cssArray = scriptObj.optJSONArray("css")
                if (cssArray != null) {
                    for (j in 0 until cssArray.length()) {
                        css.add(cssArray.getString(j))
                    }
                }

                if (matches.isNotEmpty() && (js.isNotEmpty() || css.isNotEmpty())) {
                    contentScripts.add(ContentScriptSpec(matches, js, css))
                }
            }
        }

        // Popup Actions (MV2 browser_action/page_action, MV3 action)
        var actionPopup = ""
        val actionObj = root.optJSONObject("action") ?: root.optJSONObject("browser_action") ?: root.optJSONObject("page_action")
        if (actionObj != null) {
            actionPopup = actionObj.optString("default_popup", "")
        }

        // Options Page
        val optionsPage = root.optString("options_page", root.optString("options_ui", ""))

        return ParsedExtension(
            id = id,
            name = name,
            version = version,
            description = description,
            manifestVersion = manifestVersion,
            permissions = permissions,
            hostPermissions = hostPermissions,
            backgroundScripts = backgroundScripts,
            isServiceWorker = isServiceWorker,
            contentScripts = contentScripts,
            actionPopup = actionPopup,
            optionsPage = optionsPage,
            manifestJson = manifestJsonStr
        )
    }

    private fun generateExtensionId(name: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(name.toByteArray())
        val codeAlphabet = "abcdefghijklmnopqrstuvwxyz"
        val builder = StringBuilder()
        // Take 32 characters in abc-p range like original chrome extensions format
        for (i in 0 until 32) {
            val index = (bytes[i % bytes.size].toInt() and 0xFF) % 26
            builder.append(codeAlphabet[index])
        }
        return builder.toString()
    }
}
