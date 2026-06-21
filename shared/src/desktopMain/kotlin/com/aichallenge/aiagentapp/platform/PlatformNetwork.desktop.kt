package com.aichallenge.aiagentapp.platform

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual object PlatformNetwork {
    actual fun configureOkHttpClient(builder: Any): Any {
        val okBuilder = builder as OkHttpClient.Builder
        val trustManager = createDesktopTrustManager() ?: return okBuilder
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return okBuilder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }
}

/**
 * JVM Desktop: доверяем системным сертификатам JRE и (на macOS) Keychain,
 * включая пользовательские CA — аналог network_security_config на Android.
 */
private fun createDesktopTrustManager(): X509TrustManager? {
    val managers = buildList {
        defaultJvmTrustManager()?.let(::add)
        macOsKeychainTrustManager()?.let(::add)
    }
    if (managers.isEmpty()) return null
    if (managers.size == 1) return managers.first()
    return CompositeTrustManager(managers)
}

private fun defaultJvmTrustManager(): X509TrustManager? =
    runCatching {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }.getOrNull()

private fun macOsKeychainTrustManager(): X509TrustManager? =
    runCatching {
        val keyStore = KeyStore.getInstance("KeychainStore", "Apple")
        keyStore.load(null, null)
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }.getOrNull()

private class CompositeTrustManager(
    private val delegates: List<X509TrustManager>
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        var last: CertificateException? = null
        for (delegate in delegates) {
            try {
                delegate.checkClientTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                last = e
            }
        }
        throw last ?: CertificateException("Client certificate not trusted")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        var last: CertificateException? = null
        for (delegate in delegates) {
            try {
                delegate.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                last = e
            }
        }
        throw last ?: CertificateException("Server certificate not trusted")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegates.flatMap { it.acceptedIssuers.asList() }.distinct().toTypedArray()
}
