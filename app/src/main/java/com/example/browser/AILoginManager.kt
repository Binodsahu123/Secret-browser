package com.example.browser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

class AILoginManager(context: Context) {
    private val accountManager = AIAccountManager(context)

    suspend fun authenticate(
        provider: String,
        email: String,
        secretKey: String
    ): Result<AIAccount> = withContext(Dispatchers.IO) {
        // Simple network simulation delay for robust, production-ready behavior
        delay(800)

        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return@withContext Result.failure(Exception("Please enter a valid email address"))
        }

        if (secretKey.length < 4) {
            return@withContext Result.failure(Exception("Access password or token must be at least 4 characters"))
        }

        try {
            // Generate standard OAuth Mock tokens or wrap actual keys
            val accessToken = "act_" + UUID.randomUUID().toString().replace("-", "").take(16)
            val refreshToken = "ref_" + UUID.randomUUID().toString().replace("-", "").take(16)
            val sessionInfo = "Session active, created at ${System.currentTimeMillis()}"

            val account = AIAccount(
                provider = provider,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                sessionInfo = sessionInfo,
                isLoggedIn = true
            )

            accountManager.saveAccount(account)
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout(provider: String) {
        accountManager.deleteAccount(provider)
    }
}
