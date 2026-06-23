package com.biliclient.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 极验验证 WebView（doc 04）：加载 assets/captcha.html，注入 gt/challenge，回收 validate/seccode。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestWebView(
    gt: String,
    challenge: String,
    modifier: Modifier = Modifier,
    onSuccess: (validate: String, seccode: String) -> Unit,
    onError: (String) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onSuccess(validate: String, seccode: String) { post { onSuccess(validate, seccode) } }
                    @JavascriptInterface
                    fun onError(msg: String) { post { onError(msg) } }
                }, "AndroidBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val g = gt.replace("'", "")
                        val c = challenge.replace("'", "")
                        view.evaluateJavascript("startGeetest('$g','$c')", null)
                    }
                }
                loadUrl("file:///android_asset/captcha.html")
            }
        }
    )
}
