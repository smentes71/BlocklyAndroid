package com.example.blocklyapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.*
import kotlin.random.Random

class BleManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_PERIOD: Long = 10000 // 10 saniye
        private const val CHUNK_SIZE = 80
        private const val CHUNK_DELAY = 200L // 200ms
        
        // ESP32 BLE servisi ve karakteristik UUID'leri (kendi UUID'lerinizi kullanın)
        private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        private val CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321")
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    
    private var isScanning = false
    private var isConnected = false
    private var isSending = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Callback'ler
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onDataSent: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onProgress: ((Int, String) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null
    
    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }
    
    // Bluetooth izinlerini kontrol et
    fun hasPermissions(): Boolean {
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
        
        return permissions.all { 
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        }
    }
    
    // BLE cihaz tarama
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions()) {
            onError?.invoke("Bluetooth izinleri gerekli!")
            return
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            onError?.invoke("Bluetooth kapalı!")
            return
        }
        
        if (isScanning) return
        
        onLog?.invoke("🔍 BLE cihaz taraması başlatılıyor...")
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
            
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )
        
        isScanning = true
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        
        // 10 saniye sonra taramayı durdur
        handler.postDelayed({
            stopScan()
        }, SCAN_PERIOD)
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning) {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            onLog?.invoke("🔍 BLE tarama durduruldu")
        }
    }
    
    // BLE tarama callback'i
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            onLog?.invoke("📱 Cihaz bulundu: ${device.name ?: "Bilinmeyen"} (${device.address})")
            
            // İlk bulunan cihaza bağlan
            stopScan()
            connectToDevice(device)
        }
        
        override fun onScanFailed(errorCode: Int) {
            onError?.invoke("BLE tarama hatası: $errorCode")
            isScanning = false
        }
    }
    
    // Cihaza bağlanma
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        onLog?.invoke("🔗 Bağlanıyor: ${device.name ?: device.address}")
        
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    // GATT callback'i
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onLog?.invoke("✅ Bağlantı kuruldu!")
                    isConnected = true
                    onConnectionStateChanged?.invoke(true)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    onLog?.invoke("❌ Bağlantı kesildi!")
                    isConnected = false
                    onConnectionStateChanged?.invoke(false)
                    cleanup()
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                writeCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                
                if (writeCharacteristic != null) {
                    onLog?.invoke("🔧 Servis ve karakteristik bulundu!")
                } else {
                    onError?.invoke("Karakteristik bulunamadı!")
                }
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("📤 Veri gönderildi")
            } else {
                onError?.invoke("Veri gönderme hatası: $status")
            }
        }
    }
    
    // Session ID oluşturma
    private fun generateSessionId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..9).map { chars.random() }.joinToString("")
    }
    
    // Veriyi parçalama
    private fun chunkData(data: String, chunkSize: Int = CHUNK_SIZE): List<String> {
        val chunks = mutableListOf<String>()
        for (i in data.indices step chunkSize) {
            val end = minOf(i + chunkSize, data.length)
            chunks.add(data.substring(i, end))
        }
        return chunks
    }
    
    // Parçalı JSON gönderme
    @SuppressLint("MissingPermission")
    fun sendChunkedJson(jsonData: String) {
        if (!isConnected || writeCharacteristic == null) {
            onError?.invoke("Önce ESP32 ile bağlantı kurun!")
            return
        }
        
        if (isSending) {
            onError?.invoke("Zaten veri gönderiliyor!")
            return
        }
        
        // JSON geçerliliğini kontrol et
        try {
            JSONObject(jsonData)
        } catch (e: Exception) {
            onError?.invoke("Geçersiz JSON formatı!")
            return
        }
        
        scope.launch {
            isSending = true
            
            try {
                val sessionId = generateSessionId()
                val chunks = chunkData(jsonData, CHUNK_SIZE)
                
                onLog?.invoke("📦 Veri ${chunks.size} parçaya bölündü (Session: $sessionId)")
                onProgress?.invoke(0, "Gönderim başlıyor...")
                
                for (i in chunks.indices) {
                    val chunkMessage = JSONObject().apply {
                        put("sessionId", sessionId)
                        put("chunkIndex", i)
                        put("totalChunks", chunks.size)
                        put("data", chunks[i])
                    }
                    
                    val messageString = chunkMessage.toString()
                    val messageBytes = messageString.toByteArray(Charsets.UTF_8)
                    
                    onLog?.invoke("📤 Parça ${i + 1}/${chunks.size}: ${messageBytes.size} byte")
                    
                    // Ana thread'de BLE yazma işlemi yap
                    withContext(Dispatchers.Main) {
                        writeCharacteristic?.value = messageBytes
                        bluetoothGatt?.writeCharacteristic(writeCharacteristic)
                    }
                    
                    val progress = ((i + 1) * 100) / chunks.size
                    onProgress?.invoke(progress, "Gönderiliyor... $progress%")
                    
                    // Parçalar arası bekleme
                    delay(CHUNK_DELAY)
                }
                
                onLog?.invoke("✅ Tüm parçalar başarıyla gönderildi!")
                onDataSent?.invoke("${chunks.size} parça halinde JSON başarıyla gönderildi!")
                
            } catch (e: Exception) {
                onError?.invoke("Gönderim hatası: ${e.message}")
                Log.e(TAG, "Gönderim hatası", e)
            } finally {
                isSending = false
                onProgress?.invoke(100, "Tamamlandı")
            }
        }
    }
    
    // Örnek JSON oluşturma
    fun createSampleJson(): String {
        val sampleJson = JSONObject().apply {
            put("code", """
                from machine import Pin
                import time
                
                # LED pin tanımla
                led = Pin(2, Pin.OUT)
                
                # 10 kez yanıp söndür
                for i in range(10):
                    led.on()
                    time.sleep(0.5)
                    led.off()
                    time.sleep(0.5)
                    print(f"LED {i+1}. kez yanıp söndü")
                
                print("LED test tamamlandı!")
            """.trimIndent())
            put("description", "ESP32 LED test kodu - 10 kez yanıp söndürür")
            put("author", "BLE Test")
            put("version", "1.0.0")
            put("timestamp", System.currentTimeMillis())
        }
        
        return sampleJson.toString(2)
    }
    
    // Bağlantıyı kapat
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        cleanup()
    }
    
    private fun cleanup() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        isConnected = false
        isSending = false
    }
    
    // Kaynakları temizle
    fun destroy() {
        scope.cancel()
        stopScan()
        disconnect()
    }
}