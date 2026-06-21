package com.aichallenge.aiagentapp.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File

private var androidContext: Context? = null
private var cachedApiKeys: ApiKeys? = null

fun initAndroidPlatform(context: Context, apiKeys: ApiKeys) {
    androidContext = context.applicationContext
    cachedApiKeys = apiKeys
}

actual fun platformAppDataDir(): File =
    androidContext?.filesDir
        ?: error("Android platform not initialized. Call initAndroidPlatform() first.")

actual fun platformReadApiKeys(): ApiKeys =
    cachedApiKeys ?: error("Android platform not initialized. Call initAndroidPlatform() first.")

actual fun platformCopyToClipboard(text: String) {
    val ctx = androidContext ?: return
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
}
