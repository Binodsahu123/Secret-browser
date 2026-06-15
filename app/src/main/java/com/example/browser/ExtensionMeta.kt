package com.example.browser

data class ExtensionMeta(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val size: String,
    val provider: String,
    val lastUpdated: String,
    val permissionDescription: String,
    val defaultInstalled: Boolean,
    val iconPath: String
)

fun getFullExtensionsList(viewModel: BrowserViewModel): List<ExtensionMeta> {
    val fallbackList = viewModel.getLocalFallbackExtensions()
    val dbList = viewModel.getInstalledDbExtensions().map { dbExt ->
        ExtensionMeta(
            id = dbExt.id,
            name = dbExt.name,
            description = dbExt.description ?: "User Loaded Extension",
            version = dbExt.version ?: "1.0",
            size = "N/A",
            provider = "External CRX",
            lastUpdated = "Today",
            permissionDescription = "Standard permissions",
            defaultInstalled = false,
            iconPath = ""
        )
    }
    val all = fallbackList.toMutableList()
    dbList.forEach { db ->
        if (all.none { it.id == db.id }) {
            all.add(db)
        }
    }
    return all
}

fun isExtensionInstalled(viewModel: BrowserViewModel, id: String): Boolean {
    val active = viewModel.getInstalledDbExtensions()
    if (active.any { it.id == id }) return true
    val fallback = viewModel.getLocalFallbackExtensions().find { it.id == id }
    if (fallback != null) {
        if (fallback.defaultInstalled) return true
    }
    return viewModel.isExtensionEnabled(id)
}

