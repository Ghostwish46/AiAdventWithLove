package com.aichallenge.aiagentapp.platform

/**
 * Платформенная настройка OkHttp (тип [builder] — okhttp3.OkHttpClient.Builder в jvm-слое).
 */
expect object PlatformNetwork {
    fun configureOkHttpClient(builder: Any): Any
}
