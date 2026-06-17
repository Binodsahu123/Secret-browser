package com.example.securityengine

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

interface SecurityEngine {
    fun isUrlSafe(url: String): Boolean
    fun checkCertificate(url: String): CertificateCheckResult
    fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?): Boolean
}

data class CertificateCheckResult(
    val isValid: Boolean,
    val subject: String = "",
    val issuer: String = "",
    val error: String = ""
)

object OrionSecurityEngine : SecurityEngine {
    private const val TAG = "OrionSecurityEngine"

    // High frequency Threat database of suspicious, scams, and banking copycats
    private val blacklistedPatterns = listOf(
        "malware-example.com",
        "phishing-test.org",
        "suspect-site.net",
        "update-chrome-security.com",
        "login-paypal-security",
        "verify-visa-card",
        "free-gift-rewards.top",
        "cryptoclaim",
        "bank-security-alert"
    )

    override fun isUrlSafe(url: String): Boolean {
        val lowerUrl = url.lowercase()
        for (pattern in blacklistedPatterns) {
            if (lowerUrl.contains(pattern)) {
                Log.w(TAG, "Secured blocking: URL matches known blacklisted pattern: $pattern")
                return false
            }
        }
        return true
    }

    override fun checkCertificate(url: String): CertificateCheckResult {
        if (!url.startsWith("https://")) {
            return CertificateCheckResult(isValid = false, error = "Insecure connection (HTTP)")
        }
        return try {
            val destinationUrl = URL(url)
            val connection = destinationUrl.openConnection() as HttpsURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()

            val certs = connection.serverCertificates
            if (certs.isNotEmpty() && certs[0] is X509Certificate) {
                val x509 = certs[0] as X509Certificate
                x509.checkValidity() // Throws CertificateExpiredException or CertificateNotYetValidException if invalid
                CertificateCheckResult(
                    isValid = true,
                    subject = x509.subjectDN.name,
                    issuer = x509.issuerDN.name
                )
            } else {
                CertificateCheckResult(isValid = false, error = "No valid SSL X509 certificates received.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSL Certificate check failed: ${e.message}")
            CertificateCheckResult(isValid = false, error = e.localizedMessage ?: "Unknown certificate hierarchy exception")
        }
    }

    override fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?): Boolean {
        // Anti-malware scanner: detect hidden executable payloads mimicking normal document files
        val lowerUrl = url.lowercase()
        val cdLower = contentDisposition?.lowercase() ?: ""
        val mimeLower = mimeType?.lowercase() ?: ""

        // Double extension detection e.g. "report.pdf.exe" or "invoice.txt.apk"
        val hasDoubleExtension = """\.(pdf|doc|docx|txt|jpg|png|zip)\.(exe|apk|bat|sh|cmd|vbs)$""".toRegex().containsMatchIn(lowerUrl) ||
                                 """\.(pdf|doc|docx|txt|jpg|png|zip)\.(exe|apk|bat|sh|cmd|vbs)$""".toRegex().containsMatchIn(cdLower)

        if (hasDoubleExtension) {
            Log.e(TAG, "Malicious design blocked: Double extension mimicking spoof file found.")
            return false
        }

        // Catch executable payload mime types acting as documents
        if (mimeLower.contains("application/vnd.android.package-archive") && (lowerUrl.contains("document") || lowerUrl.contains("pdf"))) {
            Log.e(TAG, "Threat found: Executable APK download masquerading as a document.")
            return false
        }

        return true
    }
}

class SafeBrowsing : SecurityEngine {
    override fun isUrlSafe(url: String): Boolean = OrionSecurityEngine.isUrlSafe(url)
    override fun checkCertificate(url: String): CertificateCheckResult = OrionSecurityEngine.checkCertificate(url)
    override fun isDownloadSafe(url: String, contentDisposition: String?, mimeType: String?): Boolean =
        OrionSecurityEngine.isDownloadSafe(url, contentDisposition, mimeType)
}
