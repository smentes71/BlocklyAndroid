package com.example.blocklyapp

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.example.blocklyapp.ui.theme.BlocklyAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BlocklyAppTheme {
                WebViewPage()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPage() {
    AndroidView(factory = { context ->
        WebView(context).apply {
            val webSettings: WebSettings = settings

            this.settings.javaScriptEnabled = true

            loadUrl("file:///android_asset/index.html")
        }
    })
}
