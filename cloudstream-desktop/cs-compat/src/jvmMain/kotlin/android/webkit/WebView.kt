package android.webkit

import android.content.Context

/** Android's `ValueCallback`, used for cookie and JavaScript results. */
interface ValueCallback<T> {
    fun onReceiveValue(value: T?)
}

/** Android's `CookieManager`. Extensions read cookies the WebView solved; there is none. */
open class CookieManager protected constructor() {
    open fun getCookie(url: String?): String? = null

    open fun setCookie(url: String?, value: String?) = Unit

    open fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        callback?.onReceiveValue(true)
    }

    open fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        callback?.onReceiveValue(true)
    }

    open fun flush() = Unit

    companion object {
        private val instance = CookieManager()

        @JvmStatic
        fun getInstance(): CookieManager = instance
    }
}

open class WebSettings {
    open fun setJavaScriptEnabled(enabled: Boolean) = Unit

    open fun setDomStorageEnabled(enabled: Boolean) = Unit

    open fun setUserAgentString(userAgent: String?) = Unit

    open fun getUserAgentString(): String? = null

    open fun setLoadWithOverviewMode(overview: Boolean) = Unit

    open fun setUseWideViewPort(wide: Boolean) = Unit

    open fun setSupportZoom(support: Boolean) = Unit

    open fun setBuiltInZoomControls(enabled: Boolean) = Unit

    open fun setDisplayZoomControls(enabled: Boolean) = Unit

    open fun setMixedContentMode(mode: Int) = Unit

    open fun setMediaPlaybackRequiresUserGesture(required: Boolean) = Unit
}

/** The request type `WebViewClient.shouldInterceptRequest` receives. */
open class WebResourceRequest

open class WebResourceResponse

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false

    open fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) = Unit

    open fun onPageFinished(view: WebView?, url: String?) = Unit

    open fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) = Unit

    open fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? = null
}

open class WebChromeClient {
    open fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?): Boolean = true
}

/**
 * Android's `WebView`. There is no browser engine in the desktop JVM, so an extension that
 * drives one gets a view that loads nothing. Cloudflare challenges that depend on it fail the
 * same way they do on a device without WebView.
 */
open class WebView(context: Context?) : android.view.View() {
    private val webSettings: WebSettings = WebSettings()

    open fun loadUrl(url: String?) = Unit

    open fun loadUrl(url: String?, additionalHttpHeaders: MutableMap<String, String>?) = Unit

    open fun loadData(data: String?, mimeType: String?, encoding: String?) = Unit

    open fun evaluateJavascript(script: String?, resultCallback: ValueCallback<String>?) {
        resultCallback?.onReceiveValue("null")
    }

    open fun stopLoading() = Unit

    open fun reload() = Unit

    open fun destroy() = Unit

    open fun clearCache(includeDiskFiles: Boolean) = Unit

    open fun clearHistory() = Unit

    open fun setWebViewClient(client: WebViewClient?) = Unit

    open fun setWebChromeClient(client: WebChromeClient?) = Unit

    open fun getUrl(): String? = null

    open fun getTitle(): String? = null

    open fun getSettings(): WebSettings = webSettings

    open fun addJavascriptInterface(obj: Any?, name: String?) = Unit
}
