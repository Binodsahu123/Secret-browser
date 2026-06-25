package com.example.permissionengine

import kotlinx.coroutines.flow.Flow

class SitePermissionRepository(private val permissionDao: PermissionDao) {
    val allPermissionsFlow: Flow<List<PermissionEntity>> = permissionDao.getAllPermissionsFlow()

    suspend fun getAllPermissions(): List<PermissionEntity> = permissionDao.getAllPermissions()

    suspend fun getPermissionsByOrigin(origin: String): List<PermissionEntity> =
        permissionDao.getPermissionsByOrigin(origin)

    suspend fun getPermission(origin: String, permissionType: String): PermissionEntity? =
        permissionDao.getPermission(origin, permissionType)

    suspend fun savePermission(
        origin: String,
        permissionType: String,
        decision: String,
        isTemporary: Boolean = false,
        expiryMs: Long = 0L
    ) {
        val existing = permissionDao.getPermission(origin, permissionType)
        val entity = PermissionEntity(
            id = existing?.id ?: 0,
            origin = origin,
            permissionType = permissionType,
            decision = decision,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = expiryMs,
            isTemporary = isTemporary
        )
        permissionDao.insertPermission(entity)
    }

    suspend fun deletePermission(origin: String, permissionType: String) =
        permissionDao.deletePermission(origin, permissionType)

    suspend fun deletePermissionsForOrigin(origin: String) =
        permissionDao.deletePermissionsForOrigin(origin)

    suspend fun clearAll() = permissionDao.clearAll()
}
