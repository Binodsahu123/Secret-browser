package com.example.extensionengine

/**
 * Interface that mediates permission requests originating from web domains and extensions.
 */
interface PermissionEngine {
    /**
     * Grants or denies permission requests for resources like camera, mic, geolocation, or storage.
     */
    fun grantPermission(origin: String, permission: String, autoGrant: Boolean)

    /**
     * Revokes previous permissions that were saved.
     */
    fun revokePermission(origin: String, permission: String)

    /**
     * Checks if a origin holds a given permission.
     */
    fun hasPermission(origin: String, permission: String): Boolean
}
