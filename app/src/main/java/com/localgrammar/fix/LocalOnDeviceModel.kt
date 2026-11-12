package com.localgrammar.fix

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs the bundled quantized T5 model in WebView's local ONNX Runtime WebAssembly backend. */
class LocalOnDeviceModel(context: Context) {
    private val appContext = context.applicationContext
    private val pageReady = CompletableDeferred<Unit>()
    private val pending = AtomicReference<CancellableContinuation<String>?>(null)
    @Volatile private var statusCallback: ((String) -> Unit)? = null
    private var started = false

    val webView: WebView = WebView(context).apply {
        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        isFocusable = false
        importantForAccessibility = WebView.IMPORTANT_FOR_ACCESSIBILITY_NO
        alpha = 0f
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
            .build()
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                url: String
            ) = assetLoader.shouldInterceptRequest(android.net.Uri.parse(url))
        }
        webChromeClient = WebChromeClient()
        addJavascriptInterface(Bridge(), "NativeModel")
    }

    fun start() {
        if (started) return
        started = true
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    suspend fun correct(text: String, onProgress: (String) -> Unit): String {
        onProgress("Preparing the offline AI model…")
        withTimeout(30_000) { pageReady.await() }

        return withTimeout(180_000) {
            suspendCancellableCoroutine { continuation ->
                if (!pending.compareAndSet(null, continuation)) {
                    continuation.resumeWithException(IllegalStateException("A correction is already running."))
                    return@suspendCancellableCoroutine
                }
                statusCallback = onProgress
                continuation.invokeOnCancellation {
                    pending.compareAndSet(continuation, null)
                    statusCallback = null
                }
                webView.post {
                    val argument = JSONObject.quote(text)
                    webView.evaluateJavascript("window.correctText($argument)", null)
                }
            }
        }
    }

    fun close() {
        val running = pending.getAndSet(null)
        if (running?.isActive == true) {
            running.resumeWithException(IllegalStateException("Correction was closed."))
        }
        statusCallback = null
        webView.stopLoading()
        webView.removeJavascriptInterface("NativeModel")
        webView.destroy()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun pageReady() {
            pageReady.complete(Unit)
        }

        @JavascriptInterface
        fun onProgress(message: String) {
            statusCallback?.invoke(message)
        }

        @JavascriptInterface
        fun onResult(text: String) {
            statusCallback = null
            val continuation = pending.getAndSet(null) ?: return
            if (continuation.isActive) continuation.resume(text)
        }

        @JavascriptInterface
        fun onFailure(message: String) {
            statusCallback = null
            val continuation = pending.getAndSet(null) ?: return
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException(message))
            }
        }
    }
}
