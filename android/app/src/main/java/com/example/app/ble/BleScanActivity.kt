package com.example.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt // BleConnectionListener 사용 위해 추가
import android.bluetooth.BluetoothGattCharacteristic // BleConnectionListener 사용 위해 추가
import android.bluetooth.BluetoothGattDescriptor // BleConnectionListener 사용 위해 추가
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile // BleConnectionListener 사용 위해 추가
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R

class BleScanActivity : AppCompatActivity(), BleConnectionManager.BleConnectionListener { // 리스너 인터페이스 구현

    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var scanButton: Button
    private lateinit var bleDeviceListAdapter: BleDeviceListAdapter
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000

    private var connectingDevice: BluetoothDevice? = null // 현재 연결 시도 중인 기기

    // --- 상수 정의를 위한 companion object 추가 ---
    companion object {
        const val EXTRA_DEVICE_NAME = "com.example.app.ble.EXTRA_DEVICE_NAME"
        const val EXTRA_DEVICE_ADDRESS = "com.example.app.ble.EXTRA_DEVICE_ADDRESS"
        private const val TAG = "BleScanActivity"
        // REQUEST_ENABLE_BT 상수는 requestEnableBluetoothLauncher를 사용하므로 현재 코드에서는 불필요
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach { entry ->
                if (!entry.value) {
                    allGranted = false
                    Log.w(TAG, "Permission not granted: ${entry.key}")
                }
            }
            if (allGranted) {
                Log.d(TAG, "All permissions granted.")
                initializeBluetoothAndAttemptScan() // 권한 획득 후 스캔 시도
            } else {
                Toast.makeText(this, "Permissions are required for BLE scanning.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestEnableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled by user.")
                initBluetoothScanner()
            } else {
                Log.w(TAG, "Bluetooth not enabled by user.")
                Toast.makeText(this, "Bluetooth must be enabled to scan for devices.", Toast.LENGTH_LONG).show()
            }
        }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                // device.name이 null이 아니거나, 필터링 로직이 있다면 추가
                bleDeviceListAdapter.addDevice(device)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach { result ->
                result.device?.let { device ->
                    bleDeviceListAdapter.addDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE Scan Failed with error code: $errorCode")
            Toast.makeText(this@BleScanActivity, "BLE Scan Failed: $errorCode", Toast.LENGTH_LONG).show()
            isScanning = false
            scanButton.text = getString(R.string.start_scan_button_text) // "Start Scan"
            handler.removeCallbacksAndMessages(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_scan)

        // BleConnectionManager 초기화 (Application 클래스에서 하는 것이 더 좋음)
        BleConnectionManager.init(applicationContext)

        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        scanButton = findViewById(R.id.scanButton)
        scanButton.text = getString(R.string.start_scan_button_text)

        setupRecyclerView()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scanButton.setOnClickListener {
            if (BleConnectionManager.isConnected()) {
                Log.d(TAG, "Already connected to a device. Disconnecting first.")
                BleConnectionManager.disconnect() // 연결된 상태면 연결 해제 시도
                // 연결 해제 후 바로 스캔을 시작할지, 아니면 사용자가 다시 누르도록 할지 결정
                // 여기서는 연결 해제 후 스캔 버튼 텍스트만 바꾸고, 사용자가 다시 누르면 스캔하도록 함
                scanButton.text = getString(R.string.start_scan_button_text) // "Start Scan"
                Toast.makeText(this, "기존 연결 해제 중...", Toast.LENGTH_SHORT).show()

            } else if (isScanning) {
                stopBleScan()
            } else {
                initializeBluetoothAndAttemptScan()
            }
        }
        // 리스너 등록
        BleConnectionManager.registerListener(this)
    }

    private fun initializeBluetoothAndAttemptScan() {
        if (checkAndRequestPermissions()) {
            if (bluetoothAdapter?.isEnabled == true) {
                initBluetoothScanner()
                if (bluetoothLeScanner != null) {
                    startBleScan()
                } else {
                    Toast.makeText(this, "BLE Scanner not available.", Toast.LENGTH_LONG).show()
                }
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestEnableBluetoothLauncher.launch(enableBtIntent)
            }
        }
    }

    private fun initBluetoothScanner() {
        if (bluetoothLeScanner == null && bluetoothAdapter?.isEnabled == true) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                Log.e(TAG, "Failed to get BluetoothLeScanner.")
                Toast.makeText(this, "Failed to initialize BLE scanner.", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "BluetoothLeScanner initialized.")
            }
        }
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION // API 30 이하 스캔에 필수
            )
        }

    private fun checkAndRequestPermissions(): Boolean {
        // API 23 (M) 미만은 런타임 권한 필요 없음
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All required permissions are already granted.")
            true
        } else {
            Log.d(TAG, "Requesting missing permissions: ${missingPermissions.joinToString()}")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            false
        }
    }

    @SuppressLint("MissingPermission") // 권한은 이미 확인됨
    private fun setupRecyclerView() {
        bleDeviceListAdapter = BleDeviceListAdapter(discoveredDevices) { device ->
            // 권한 체크는 이미 수행되었다고 가정 (BLUETOOTH_CONNECT)
            val deviceNameString = device.name ?: "Unknown Device"
            Log.d(TAG, "Device selected: $deviceNameString (${device.address})")
            Toast.makeText(this, "Connecting to: $deviceNameString", Toast.LENGTH_SHORT).show()

            if (isScanning) {
                stopBleScan()
            }
            connectingDevice = device // 연결 시도 중인 기기 저장
            BleConnectionManager.connect(device.address)
            // 연결 상태 변화는 BleConnectionListener 콜백에서 처리
        }
        devicesRecyclerView.adapter = bleDeviceListAdapter
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (isScanning) return
        if (bluetoothLeScanner == null) {
            initBluetoothScanner() // 스캐너 재초기화 시도
            if (bluetoothLeScanner == null) {
                Log.e(TAG, "BluetoothLeScanner not initialized after re-attempt. Cannot start scan.")
                Toast.makeText(this, "Scanner not ready. Please try again.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        Log.i(TAG, "Starting BLE Scan.")
        discoveredDevices.clear()
        bleDeviceListAdapter.clearDevices() // 어댑터의 데이터도 클리어

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // val scanFilters: List<ScanFilter> = ... (필요시 필터 설정)
        bluetoothLeScanner?.startScan(null, scanSettings, scanCallback) // 필터 없이 스캔

        isScanning = true
        scanButton.text = getString(R.string.stop_scan_button_text) // "Stop Scan"
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()

        handler.postDelayed({
            if (isScanning) {
                Log.i(TAG, "Scan timed out.")
                stopBleScan()
            }
        }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!isScanning && bluetoothLeScanner == null) {
            isScanning = false
            scanButton.text = getString(R.string.start_scan_button_text)
            handler.removeCallbacksAndMessages(null)
            return
        }
        if (!isScanning) return

        Log.i(TAG, "Stopping BLE Scan.")
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        scanButton.text = getString(R.string.start_scan_button_text)
        handler.removeCallbacksAndMessages(null)
        Toast.makeText(this, "Scan stopped.", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (!BleConnectionManager.isConnected()) { // 연결되어 있지 않을 때만 스캐너/스캔 버튼 상태 업데이트
            if (checkAndRequestPermissions() && bluetoothAdapter?.isEnabled == true) {
                initBluetoothScanner()
            }
            scanButton.text = if (isScanning) getString(R.string.stop_scan_button_text) else getString(R.string.start_scan_button_text)
        } else {
            // 이미 다른 기기에 연결되어 있다면, UI는 그 상태를 반영해야 함 (예: "Disconnect" 버튼)
            // 현재 코드는 BleConnectionManager.disconnect() 호출 시 STATE_DISCONNECTED 에서 scanButton 텍스트 변경
            scanButton.text = getString(R.string.disconnect_button_text) // "Disconnect"
            Log.d(TAG, "onResume: Already connected to ${BleConnectionManager.connectedDevice?.address}")
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning && !BleConnectionManager.isConnected()) { // 연결 중이 아닐 때만 스캔 중지
            stopBleScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BleConnectionManager.unregisterListener(this) // 리스너 해제
        // BleConnectionManager.disconnect() // Activity 종료 시 연결 자동 해제 여부 결정
    }

    // --- BleConnectionManager.BleConnectionListener 구현 ---
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?) {
        val device = gatt?.device
        runOnUiThread {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (device?.address == connectingDevice?.address) {
                        Log.i(TAG, "Connected to ${device?.name ?: "Unknown"} (${device?.address})")
                        Toast.makeText(this, "Connected to ${device?.name ?: "Unknown"}", Toast.LENGTH_SHORT).show()
                        scanButton.text = getString(R.string.disconnect_button_text) // "Disconnect"
                        // 서비스 발견은 onServicesDiscovered에서 처리
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // 이전에 연결 시도했던 기기 또는 현재 연결되어 있던 기기로부터 연결이 끊겼을 때
                    val deviceName = device?.name ?: connectingDevice?.name ?: BleConnectionManager.connectedDevice?.name ?: "Device"
                    Log.i(TAG, "Disconnected from $deviceName (${device?.address ?: connectingDevice?.address ?: BleConnectionManager.connectedDevice?.address})")
                    Toast.makeText(this, "$deviceName 연결 끊김", Toast.LENGTH_SHORT).show()
                    scanButton.text = getString(R.string.start_scan_button_text) // "Start Scan"
                    connectingDevice = null
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    if (device?.address == connectingDevice?.address) {
                        Log.d(TAG, "Connecting to ${device?.name ?: "Unknown"}...")
                        Toast.makeText(this, "${device?.name ?: "Unknown"} 연결 중...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        val device = gatt?.device
        if (device?.address == connectingDevice?.address) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered for ${device?.name ?: "Unknown"}. Navigating to DeviceControlActivity.")
                Toast.makeText(this, "${device?.name ?: "Unknown"} 서비스 발견", Toast.LENGTH_SHORT).show()

                val intent = Intent(this@BleScanActivity, DeviceControlActivity::class.java).apply {
                    // 권한 체크는 이미 수행됨
                    putExtra(EXTRA_DEVICE_NAME, device?.name ?: "Unknown Device")
                    putExtra(EXTRA_DEVICE_ADDRESS, device?.address)
                }
                startActivity(intent)
                connectingDevice = null // DeviceControlActivity로 넘어갔으므로 초기화
            } else {
                Log.w(TAG, "Service discovery failed for ${device?.name ?: "Unknown"} with status: $status")
                Toast.makeText(this, "서비스 발견 실패: $status", Toast.LENGTH_SHORT).show()
                BleConnectionManager.disconnect() // 서비스 발견 실패 시 연결 해제
            }
        }
    }

    // DeviceControlActivity에서 주로 사용하므로, 여기서는 간단한 로그만 남기거나 비워둠
    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int) {
        Log.d(TAG, "onCharacteristicRead in BleScanActivity - (UUID: ${characteristic?.uuid}, Status: $status). Usually handled in DeviceControlActivity.")
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        Log.d(TAG, "onCharacteristicWrite in BleScanActivity - (UUID: ${characteristic?.uuid}, Status: $status). Usually handled in DeviceControlActivity.")
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?) {
        Log.d(TAG, "onCharacteristicChanged in BleScanActivity - (UUID: ${characteristic?.uuid}). Usually handled in DeviceControlActivity.")
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        Log.d(TAG, "onDescriptorWrite in BleScanActivity - (UUID: ${descriptor?.uuid}, Status: $status). Usually handled in DeviceControlActivity.")
    }
}

// strings.xml 에 다음 내용 추가 필요
// <string name="start_scan_button_text">Start Scan</string>
// <string name="stop_scan_button_text">Stop Scan</string>
// <string name="disconnect_button_text">Disconnect</string>
