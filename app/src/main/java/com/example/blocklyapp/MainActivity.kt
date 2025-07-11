package com.example.blocklyapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.blocklyapp.ui.theme.BlocklyAppTheme

class MainActivity : ComponentActivity() {
    
    lateinit var bleManager: BleManager
    
    // İzin isteme launcher'ı
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // İzinler verildi, BLE işlemlerini başlat
        } else {
            // İzinler reddedildi
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        bleManager = BleManager(this)
        setupBleCallbacks()
        
        // İzinleri kontrol et ve iste
        checkAndRequestPermissions()

        setContent {
            BlocklyAppTheme {
                MainScreen()
            }
        }
    }
    
    private fun setupBleCallbacks() {
        bleManager.onConnectionStateChanged = { connected ->
            runOnUiThread {
                // Bağlantı durumu değişti
            }
        }
        
        bleManager.onDataSent = { message ->
            runOnUiThread {
                // Veri gönderildi
            }
        }
        
        bleManager.onError = { error ->
            runOnUiThread {
                // Hata oluştu
            }
        }
        
        bleManager.onProgress = { progress, message ->
            runOnUiThread {
                // İlerleme güncellendi
            }
        }
        
        bleManager.onLog = { log ->
            runOnUiThread {
                Log.d("BLE", log)
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        if (!bleManager.hasPermissions()) {
            val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            requestPermissionLauncher.launch(permissions)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bleManager.destroy()
    }
}

@Composable
fun MainScreen() {
    var showBleControls by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // BLE kontrol butonları
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { showBleControls = !showBleControls }
            ) {
                Text(if (showBleControls) "WebView'i Göster" else "BLE Kontrollerini Göster")
            }
        }
        
        if (showBleControls) {
            BleControlsPage()
        } else {
            WebViewPage()
        }
    }
}

@Composable
fun BleControlsPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as MainActivity
    val bleManager = activity.bleManager
    
    var isConnected by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Bağlantı yok") }
    var progress by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(listOf<String>()) }
    
    // BLE callback'lerini güncelle
    LaunchedEffect(Unit) {
        bleManager.onConnectionStateChanged = { connected ->
            isConnected = connected
            connectionStatus = if (connected) "Bağlandı" else "Bağlantı yok"
        }
        
        bleManager.onProgress = { prog, text ->
            progress = prog
            progressText = text
        }
        
        bleManager.onLog = { log ->
            logs = logs + log
            if (logs.size > 20) {
                logs = logs.takeLast(20)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bağlantı durumu
        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Bağlantı Durumu: $connectionStatus",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { bleManager.startScan() },
                        enabled = !isConnected
                    ) {
                        Text("🔍 Cihaz Ara")
                    }
                    
                    Button(
                        onClick = { bleManager.disconnect() },
                        enabled = isConnected
                    ) {
                        Text("❌ Bağlantıyı Kes")
                    }
                }
            }
        }
        
        // Veri gönderme
        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Veri Gönderimi",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { 
                        val sampleJson = bleManager.createSampleJson()
                        bleManager.sendChunkedJson(sampleJson)
                    },
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🚀 Örnek JSON Gönder")
                }
                
                if (progress > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Log görüntüleme
        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Loglar",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    logs.takeLast(10).forEach { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
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