package com.example.app

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

@SuppressLint("MissingPermission")
class BleService : Service() {

    private val binder = LocalBinder()
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    companion object {
        const val ACTION_GATT_CONNECTED = "com.example.bluetooth.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.bluetooth.ACTION_GATT_DISCONNECTED"
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // ⭐ 중요: 이 메서드에서 close()를 호출하지 않습니다.
        // 서비스가 명시적으로 중단되거나 연결이 필요 없을 때만 close()를 호출합니다.
        Log.d("BleService", "Service Unbound. Keeping connection alive.")
        return super.onUnbind(intent)
    }

    fun startBleScan(macAddress: String) {
        if (isScanning || bluetoothAdapter == null) return

        val bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            Log.e("BleService", "BLE Scanner is not available.")
            return
        }

        val filter = ScanFilter.Builder().setDeviceAddress(macAddress).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        bleScanner.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        Log.d("BleService", "Scanning for device: $macAddress")
    }

    fun stopBleScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d("BleService", "BLE scan stopped.")
    }

    fun connect(address: String): Boolean {
        if (bluetoothAdapter == null || address.isBlank()) {
            Log.e("BleService", "BluetoothAdapter not initialized or address is invalid.")
            return false
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.e("BleService", "Device not found.")
            return false
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        return true
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("BleService", "Found device: ${result.device.address}")
            stopBleScan()
            connect(result.device.address)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BleService", "GATT Connected!")
                    broadcastUpdate(ACTION_GATT_CONNECTED)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BleService", "GATT Disconnected.")
                    broadcastUpdate(ACTION_GATT_DISCONNECTED)
                }
            }
        }
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }
}