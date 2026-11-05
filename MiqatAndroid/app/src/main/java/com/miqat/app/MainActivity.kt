package com.miqat.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pageReady = false
    private var pendingTab: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scheduler.ensureChannel(this)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                pendingTab?.let { openTab(it) }
                pendingTab = null
            }
        }
        setContentView(web)

        pendingTab = intent?.getStringExtra("tab")
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("file:///android_asset/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("tab")?.let { if (pageReady) openTab(it) else pendingTab = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun openTab(tab: String) {
        val safe = tab.filter { it.isLetter() }
        web.evaluateJavascript("window.miqatOpenTab && miqatOpenTab('$safe')", null)
    }

    inner class Bridge {
        @JavascriptInterface
        fun saveSettings(json: String) {
            Scheduler.saveSettings(applicationContext, json)
        }

        @JavascriptInterface
        fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
            }
        }
    }
}
