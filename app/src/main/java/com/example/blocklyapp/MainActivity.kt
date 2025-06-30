package com.example.blocklyapp

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
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
            // WebView ayarları
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                
                // Güvenlik ayarları
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                
                // Performans ayarları
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                
                // Klavye ve input ayarları
                setSupportZoom(true)
                builtInZoomControls = false
                displayZoomControls = false
                
                // Text input için gerekli ayarlar
                javaScriptCanOpenWindowsAutomatically = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            // Focus ve touch ayarları
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus(View.FOCUS_DOWN)

            // WebViewClient - sayfa yükleme kontrolü
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebView", "Sayfa yüklendi: $url")
                    
                    // Sayfa yüklendikten sonra input alanları için JavaScript kodu çalıştır
                    evaluateJavascript("""
                        // Input alanlarına focus olduğunda klavyeyi göster
                        document.addEventListener('focusin', function(e) {
                            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
                                e.target.style.fontSize = '16px';
                                setTimeout(function() {
                                    e.target.scrollIntoView({behavior: 'smooth', block: 'center'});
                                }, 300);
                            }
                        });
                        
                        // Blockly field'ları için özel event listener
                        document.addEventListener('click', function(e) {
                            if (e.target.classList.contains('blocklyHtmlInput') || 
                                e.target.classList.contains('blocklyFieldTextInput')) {
                                e.target.focus();
                                e.target.click();
                            }
                        });
                    """, null)
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.e("WebView", "Hata: $description")
                }
            }

            // WebChromeClient - console log'ları ve input handling için
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("WebView Console", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                    return true
                }
                
                // Input field'lar için gerekli
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                }
            }

            // Touch listener ekle
            setOnTouchListener { _, _ ->
                requestFocus()
                false
            }

            // HTML dosyasını yükle
            loadUrl("file:///android_asset/index.html")
        }
    })
}