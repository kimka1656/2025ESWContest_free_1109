package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

@SuppressLint("MissingPermission") // Permissions should be checked before calling connect/read etc.
object BleConnectionManager {
    private const val TAG = "BleConnectionManager"

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var applicationContext: Context? = null

    var connectedDevice: BluetoothDevice? = null
        private set
    var connectionState: Int = BluetoothProfile.STATE_DISCONNECTED
        private set

    // 리스너 인터페이스 정의
    interface BleConnectionListener {
        fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?)
        fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int)
        fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int)
        fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int)
        fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?)
        fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int)
    }

    interface BleDataListener {
        fun onDataReceived(data: String, packetSize: Int) // 데이터와 패킷 크기 전달
        fun onDebugMessage(message: String)               // 디버그 메시지 전달
    }

    private var bleDataListener: BleDataListener? = null

    fun setBleDataListener(listener: BleDataListener?) {
        this.bleDataListener = listener
    }

    private val listeners = mutableSetOf<BleConnectionListener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- 주요 UUID 설정 ---
    val SENSOR_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcde00")
    val ACCEL_TEXT_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcde02") // 데이터 수신용
    val CMD_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcde10")      // 명령어 전송용
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")       // 알림 설정용 디스크립터

    // 앱에서 사용할 목표 UUID (기본값 설정)
    var TARGET_SERVICE_UUID: UUID = SENSOR_SERVICE_UUID
    var TARGET_READ_CHARACTERISTIC_UUID: UUID? = ACCEL_TEXT_CHAR_UUID // 필요시 사용
    var TARGET_WRITE_CHARACTERISTIC_UUID: UUID? = CMD_CHAR_UUID    // 필요시 사용
    var TARGET_NOTIFY_CHARACTERISTIC_UUID: UUID? = ACCEL_TEXT_CHAR_UUID // 데이터 알림 받을 특성

    // 요청할 MTU 값 (헤더 3바이트 포함). 예: 100 -> 실제 데이터 97바이트
    private const val REQUESTED_MTU = 100

    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
            val bluetoothManager = applicationContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth not supported on this device.")
                mainHandler.post { bleDataListener?.onDebugMessage("BLE 어댑터 없음/미지원") }
            } else {
                Log.d(TAG, "BleConnectionManager initialized.")
                mainHandler.post { bleDataListener?.onDebugMessage("BleManager 초기화됨") }
            }
        }
    }

    fun registerListener(listener: BleConnectionListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
        Log.d(TAG, "Listener registered: $listener. Current listeners: ${listeners.size}")
    }

    fun unregisterListener(listener: BleConnectionListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
        Log.d(TAG, "Listener unregistered: $listener. Current listeners: ${listeners.size}")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val deviceName = try { gatt?.device?.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
            val deviceAddress = gatt?.device?.address ?: "N/A"
            Log.d(TAG, "onConnectionStateChange: Name=$deviceName, Addr=$deviceAddress, Status=$status, NewState=$newState")
            mainHandler.post { bleDataListener?.onDebugMessage("연결상태변경: $newState (이름:$deviceName, 주소:$deviceAddress, GATT Status:$status)") }

            connectionState = newState
            connectedDevice = if (newState == BluetoothProfile.STATE_CONNECTED) gatt?.device else null

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                Log.i(TAG, "Connected to GATT server: $deviceName ($deviceAddress)")
                mainHandler.post { bleDataListener?.onDebugMessage("연결됨: $deviceName ($deviceAddress)") }

                mainHandler.postDelayed({
                    val mtuRequestedSuccessfully = bluetoothGatt?.requestMtu(REQUESTED_MTU)
                    Log.d(TAG, "Requesting MTU: $REQUESTED_MTU for $deviceAddress, Success: $mtuRequestedSuccessfully")
                    mainHandler.post { bleDataListener?.onDebugMessage("MTU 요청($REQUESTED_MTU): $mtuRequestedSuccessfully (주소:$deviceAddress)") }
                    if (mtuRequestedSuccessfully != true) {
                        Log.w(TAG, "MTU request initiation failed for $deviceAddress, discovering services with default MTU.")
                        mainHandler.post { bleDataListener?.onDebugMessage("MTU 요청시작실패(주소:$deviceAddress), 기본MTU로 서비스검색시작") }
                        bluetoothGatt?.discoverServices()
                    }
                }, 600) // 연결 안정화를 위한 약간의 딜레이

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server: $deviceName ($deviceAddress)")
                mainHandler.post { bleDataListener?.onDebugMessage("연결끊김: $deviceName ($deviceAddress)") }
                closeGatt()
            }

            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onConnectionStateChanged(newState, gatt)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onConnectionStateChanged for $deviceAddress", e)
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            val deviceAddress = gatt?.device?.address ?: "N/A"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU Changed to: $mtu for $deviceAddress (Requested: $REQUESTED_MTU)")
                mainHandler.post { bleDataListener?.onDebugMessage("MTU 변경성공: $mtu (요청:$REQUESTED_MTU, 주소:$deviceAddress)") }
            } else {
                Log.w(TAG, "MTU change FAILED, status: $status for $deviceAddress (Requested: $REQUESTED_MTU). Using default MTU.")
                mainHandler.post { bleDataListener?.onDebugMessage("MTU 변경실패: status $status (요청:$REQUESTED_MTU, 주소:$deviceAddress)") }
            }
            Log.d(TAG, "Discovering services for $deviceAddress after MTU negotiation attempt...")
            mainHandler.post { bleDataListener?.onDebugMessage("서비스 검색 시작 (MTU협상후, 주소:$deviceAddress)") }
            bluetoothGatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val deviceAddress = gatt?.device?.address ?: "N/A"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully for $deviceAddress")
                mainHandler.post { bleDataListener?.onDebugMessage("Svc 발견: $deviceAddress") }
                TARGET_NOTIFY_CHARACTERISTIC_UUID?.let { notifyCharUuid ->
                    enableNotifications(TARGET_SERVICE_UUID, notifyCharUuid)
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received error: $status for $deviceAddress")
                mainHandler.post { bleDataListener?.onDebugMessage("Svc 발견오류 $status: $deviceAddress") }
            }
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onServicesDiscovered(gatt, status)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onServicesDiscovered for $deviceAddress", e)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray, // API 33+
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            val charUuid = characteristic.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic $charUuid read (API 33+), value: ${value.toHexString()}")
            } else {
                Log.w(TAG, "Characteristic $charUuid read failed (API 33+), status: $status")
            }
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onCharacteristicRead(gatt, characteristic, value, status)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onCharacteristicRead (API 33+)", e)
                        }
                    }
                }
            }
        }

        @Deprecated("Used for API Level < 33.")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            val valueLegacy = characteristic.value
            val charUuid = characteristic.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "(Legacy) Characteristic $charUuid read, value: ${valueLegacy?.toHexString()}")
            } else {
                Log.w(TAG, "(Legacy) Characteristic $charUuid read failed, status: $status")
            }
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onCharacteristicRead(gatt, characteristic, valueLegacy, status)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onCharacteristicRead (Legacy)", e)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            val charUuid = characteristic?.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic $charUuid written successfully.")
            } else {
                Log.w(TAG, "Characteristic $charUuid write failed, status: $status")
            }
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onCharacteristicWrite(gatt, characteristic, status)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onCharacteristicWrite", e)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray // API 33+
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onCharacteristicChanged(gatt, characteristic, value)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onCharacteristicChanged (API 33+)", e)
                        }
                    }
                }

                if (characteristic.uuid == TARGET_NOTIFY_CHARACTERISTIC_UUID) {
                    val dataStringForListener = String(value, Charsets.UTF_8)
                    val packetSize = value.size
                    Log.d(TAG, "Notify (API 33+): UUID=${characteristic.uuid}, Size=$packetSize, Data='$dataStringForListener'")
                    try {
                        bleDataListener?.onDataReceived(dataStringForListener, packetSize)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling BleDataListener.onDataReceived (API 33+)", e)
                    }
                }
            }
        }

        @Deprecated("Used for API Level < 33.")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val valueLegacy = characteristic.value // API 32 이하에서는 characteristic.value 사용
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onCharacteristicChanged(gatt, characteristic, valueLegacy)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onCharacteristicChanged (Legacy)", e)
                        }
                    }
                }

                if (characteristic.uuid == TARGET_NOTIFY_CHARACTERISTIC_UUID && valueLegacy != null) {
                    val dataStringForListener = String(valueLegacy, Charsets.UTF_8)
                    val packetSize = valueLegacy.size
                    Log.d(TAG, "Notify (Legacy): UUID=${characteristic.uuid}, Size=$packetSize, Data='$dataStringForListener'")
                    try {
                        bleDataListener?.onDataReceived(dataStringForListener, packetSize)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calling BleDataListener.onDataReceived (Legacy)", e)
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            val descUuid = descriptor?.uuid?.toString() ?: "N/A"
            val charUuid = descriptor?.characteristic?.uuid?.toString() ?: "N/A"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor $descUuid written successfully for characteristic $charUuid.")
                mainHandler.post { bleDataListener?.onDebugMessage("Desc 쓰기성공: $descUuid (Char:$charUuid)") }
            } else {
                Log.w(TAG, "Descriptor write FAILED, status $status for $descUuid (Char:$charUuid).")
                mainHandler.post { bleDataListener?.onDebugMessage("Desc 쓰기실패 $status: $descUuid (Char:$charUuid)") }
            }
            mainHandler.post {
                synchronized(listeners) {
                    listeners.forEach {
                        try {
                            it.onDescriptorWrite(gatt, descriptor, status)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in BleConnectionListener onDescriptorWrite", e)
                        }
                    }
                }
            }
        }
    } // End of gattCallback

    fun connect(deviceAddress: String): Boolean {
        if (applicationContext == null) {
            Log.e(TAG, "BleConnectionManager not initialized. Call init() first.")
            mainHandler.post { bleDataListener?.onDebugMessage("BleManager 초기화 필요") }
            return false
        }
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "BluetoothAdapter not initialized or not enabled.")
            mainHandler.post { bleDataListener?.onDebugMessage("BLE 어댑터 비활성/없음") }
            return false
        }
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.w(TAG, "Device not found with address: $deviceAddress.")
            mainHandler.post { bleDataListener?.onDebugMessage("기기못찾음: $deviceAddress") }
            return false
        }

        if (bluetoothGatt != null && deviceAddress == connectedDevice?.address &&
            (connectionState == BluetoothProfile.STATE_CONNECTING || connectionState == BluetoothProfile.STATE_CONNECTED)) {
            val deviceName = try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
            Log.d(TAG, "Already connected or connecting to $deviceName ($deviceAddress). Ignoring request.")
            mainHandler.post { bleDataListener?.onDebugMessage("이미 연결(중)된 기기: $deviceName ($deviceAddress)") }
            mainHandler.post { synchronized(listeners) { listeners.forEach { it.onConnectionStateChanged(connectionState, bluetoothGatt) } } }
            return true
        }

        if (bluetoothGatt != null && deviceAddress != connectedDevice?.address) {
            val prevDeviceName = try { connectedDevice?.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
            val prevDeviceAddress = connectedDevice?.address ?: "N/A"
            Log.d(TAG, "Connecting to a new device ($deviceAddress). Disconnecting from previous device $prevDeviceName ($prevDeviceAddress).")
            mainHandler.post { bleDataListener?.onDebugMessage("새 기기 연결(${deviceAddress}), 이전($prevDeviceName, $prevDeviceAddress) 연결 해제") }
            disconnect()
        }

        val currentDeviceName = try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
        Log.i(TAG, "Attempting to connect to device: $currentDeviceName ($deviceAddress)")
        mainHandler.post { bleDataListener?.onDebugMessage("Connect 시도: $currentDeviceName ($deviceAddress)") }

        connectionState = BluetoothProfile.STATE_CONNECTING
        mainHandler.post { synchronized(listeners) { listeners.forEach { it.onConnectionStateChanged(connectionState, null) } } }

        bluetoothGatt = device.connectGatt(applicationContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (bluetoothGatt == null) {
            Log.e(TAG, "connectGatt returned null for device $currentDeviceName ($deviceAddress)")
            mainHandler.post { bleDataListener?.onDebugMessage("connectGatt() null 반환: $currentDeviceName ($deviceAddress)") }
            connectionState = BluetoothProfile.STATE_DISCONNECTED
            mainHandler.post { synchronized(listeners) { listeners.forEach { it.onConnectionStateChanged(connectionState, null) } } }
            return false
        }
        return true
    }

    fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected to any device. Cannot read characteristic $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("읽기실패: 미연결 ($characteristicUuid)")}
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid)
        if (service == null) {
            Log.w(TAG, "Service $serviceUuid not found for reading $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("읽기실패: Svc못찾음 $serviceUuid (Char:$characteristicUuid)")}
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w(TAG, "Characteristic $characteristicUuid not found in service $serviceUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("읽기실패: Char못찾음 $characteristicUuid (Svc:$serviceUuid)")}
            return
        }
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Log.w(TAG, "Characteristic $characteristicUuid is not readable.")
            mainHandler.post { bleDataListener?.onDebugMessage("읽기실패: 읽기미지원 $characteristicUuid")}
            return
        }
        if (bluetoothGatt?.readCharacteristic(characteristic) == true) {
            Log.i(TAG, "Attempting to read characteristic: $characteristicUuid")
            mainHandler.post { bleDataListener?.onDebugMessage("읽기요청성공: $characteristicUuid")}
        } else {
            Log.w(TAG, "Failed to initiate read for characteristic: $characteristicUuid")
            mainHandler.post { bleDataListener?.onDebugMessage("읽기요청실패: $characteristicUuid")}
        }
    }

    fun readTargetCharacteristic() {
        TARGET_READ_CHARACTERISTIC_UUID?.let { readCharUuid ->
            Log.d(TAG, "Reading TARGET_READ_CHARACTERISTIC_UUID: $readCharUuid")
            readCharacteristic(TARGET_SERVICE_UUID, readCharUuid)
        } ?: TARGET_NOTIFY_CHARACTERISTIC_UUID?.let { notifyCharUuid ->
            Log.d(TAG, "TARGET_READ_CHARACTERISTIC_UUID is null, attempting to read TARGET_NOTIFY_CHARACTERISTIC_UUID ($notifyCharUuid) instead.")
            readCharacteristic(TARGET_SERVICE_UUID, notifyCharUuid)
        } ?: run {
            Log.w(TAG, "No target characteristic UUID for reading is defined.")
            mainHandler.post { bleDataListener?.onDebugMessage("읽을 대상 특성 UUID 없음") }
        }
    }


    fun writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected. Cannot write characteristic $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("쓰기실패: 미연결 ($characteristicUuid)")}
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid)
        if (service == null) {
            Log.w(TAG, "Service $serviceUuid not found for writing $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("쓰기실패: Svc못찾음 $serviceUuid (Char:$characteristicUuid)")}
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            Log.w(TAG, "Characteristic $characteristicUuid not found for writing.")
            mainHandler.post { bleDataListener?.onDebugMessage("쓰기실패: Char못찾음 $characteristicUuid (Svc:$serviceUuid)")}
            return
        }
        val writeProperty = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.PROPERTY_WRITE
        }
        if ((characteristic.properties and writeProperty) == 0) {
            Log.w(TAG, "Characteristic $characteristicUuid does not support the specified write type ($writeType). Properties: ${characteristic.properties}")
            mainHandler.post { bleDataListener?.onDebugMessage("쓰기실패: 쓰기타입미지원 $writeType (Char:$characteristicUuid)")}
            return
        }

        Log.d(TAG, "Attempting to write to characteristic $characteristicUuid, Data: ${data.toHexString()}, WriteType: $writeType")
        mainHandler.post { bleDataListener?.onDebugMessage("쓰기요청: $characteristicUuid, Data:${data.toHexString()}, Type:$writeType")}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = bluetoothGatt?.writeCharacteristic(characteristic, data, writeType)
            Log.d(TAG, "writeCharacteristic (API 33+) called for $characteristicUuid, result code (0 for success): $result")
            // GATT_SUCCESS is 0. writeCharacteristic for API 33+ returns an int code.
            mainHandler.post { bleDataListener?.onDebugMessage("쓰기 결과 (API33+): $result (0=성공) (Char:$characteristicUuid)")}
        } else {
            characteristic.value = data
            characteristic.writeType = writeType
            val success = bluetoothGatt?.writeCharacteristic(characteristic)
            Log.d(TAG, "writeCharacteristic (legacy) called for $characteristicUuid, result: $success")
            mainHandler.post { bleDataListener?.onDebugMessage("쓰기 결과 (Legacy): $success (Char:$characteristicUuid)")}
        }
    }

    fun enableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected. Cannot enable notifications for $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("알림활성화실패: 미연결 ($characteristicUuid)") }
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid) ?: run {
            Log.w(TAG, "Service $serviceUuid not found for notifications on $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("알림활성화실패: Svc못찾음 $serviceUuid (Char:$characteristicUuid)") }
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid) ?: run {
            Log.w(TAG, "Characteristic $characteristicUuid not found in service $serviceUuid for notifications.")
            mainHandler.post { bleDataListener?.onDebugMessage("알림활성화실패: Char못찾음 $characteristicUuid (Svc:$serviceUuid)") }
            return
        }
        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
            Log.w(TAG, "Characteristic $characteristicUuid does not support notifications. Properties: ${characteristic.properties}")
            mainHandler.post { bleDataListener?.onDebugMessage("알림활성화실패: Notify미지원 $characteristicUuid") }
            return
        }

        if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == true) {
            Log.d(TAG, "setCharacteristicNotification(true) successful for $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("setCharNotify(true) 성공: $characteristicUuid") }
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                Log.d(TAG, "Writing ENABLE_NOTIFICATION_VALUE to descriptor $CCCD_UUID for $characteristicUuid")
                mainHandler.post { bleDataListener?.onDebugMessage("Desc쓰기요청($CCCD_UUID) for $characteristicUuid") }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = bluetoothGatt?.writeDescriptor(descriptor, enableValue)
                    Log.d(TAG, "writeDescriptor (API 33+) for $CCCD_UUID on $characteristicUuid, result code: $result")
                    mainHandler.post { bleDataListener?.onDebugMessage("Desc쓰기 결과(API33+): $result (0=성공) ($CCCD_UUID for $characteristicUuid)")}
                } else {
                    descriptor.value = enableValue
                    val success = bluetoothGatt?.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor (legacy) for $CCCD_UUID on $characteristicUuid, result: $success")
                    mainHandler.post { bleDataListener?.onDebugMessage("Desc쓰기 결과(Legacy): $success ($CCCD_UUID for $characteristicUuid)")}
                }
            } else {
                Log.w(TAG, "CCCD descriptor ($CCCD_UUID) not found for characteristic $characteristicUuid.")
                mainHandler.post { bleDataListener?.onDebugMessage("CCCD못찾음($CCCD_UUID) for $characteristicUuid") }
            }
        } else {
            Log.w(TAG, "Failed to set characteristic notification to true for $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("setCharNotify(true) 실패: $characteristicUuid") }
        }
    }

    fun disableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        if (bluetoothGatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            Log.w(TAG, "Not connected. Cannot disable notifications for $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("알림비활성화실패: 미연결 ($characteristicUuid)") }
            return
        }
        val service = bluetoothGatt?.getService(serviceUuid) ?: run {
            Log.w(TAG, "Service $serviceUuid not found for disabling notifications on $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("알림비활성화실패: Svc못찾음 $serviceUuid (Char:$characteristicUuid)") }
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid) ?: run {
            Log.w(TAG, "Characteristic $characteristicUuid not found for disabling notifications.")
            mainHandler.post { bleDataListener?.onDebugMessage("알림비활성화실패: Char못찾음 $characteristicUuid (Svc:$serviceUuid)") }
            return
        }
        if (!((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) { // Notify 또는 Indicate 지원 확인
            Log.w(TAG, "Characteristic $characteristicUuid does not support notifications or indications.")
            mainHandler.post { bleDataListener?.onDebugMessage("알림비활성화실패: Notify/Indicate미지원 $characteristicUuid") }
            return
        }

        if (bluetoothGatt?.setCharacteristicNotification(characteristic, false) == true) {
            Log.d(TAG, "setCharacteristicNotification(false) successful for $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("setCharNotify(false) 성공: $characteristicUuid") }
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val disableValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                Log.d(TAG, "Writing DISABLE_NOTIFICATION_VALUE to descriptor $CCCD_UUID for $characteristicUuid")
                mainHandler.post { bleDataListener?.onDebugMessage("Desc쓰기요청(Disable, $CCCD_UUID) for $characteristicUuid") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = bluetoothGatt?.writeDescriptor(descriptor, disableValue)
                    Log.d(TAG, "writeDescriptor (API 33+) for disabling $CCCD_UUID on $characteristicUuid, result code: $result")
                    mainHandler.post { bleDataListener?.onDebugMessage("Desc쓰기(Disable) 결과(API33+): $result (0=성공) ($CCCD_UUID for $characteristicUuid)")}
                } else {
                    descriptor.value = disableValue
                    val success = bluetoothGatt?.writeDescriptor(descriptor)
                    Log.d(TAG, "writeDescriptor (legacy) for disabling $CCCD_UUID on $characteristicUuid, result: $success")
                    mainHandler.post { bleDataListener?.onDebugMessage("Desc쓰기(Disable) 결과(Legacy): $success ($CCCD_UUID for $characteristicUuid)")}
                }
            } else {
                Log.w(TAG, "CCCD descriptor ($CCCD_UUID) not found for $characteristicUuid when disabling.")
                mainHandler.post { bleDataListener?.onDebugMessage("CCCD못찾음(Disable, $CCCD_UUID) for $characteristicUuid") }
            }
        } else {
            Log.w(TAG, "Failed to set characteristic notification to false for $characteristicUuid.")
            mainHandler.post { bleDataListener?.onDebugMessage("setCharNotify(false) 실패: $characteristicUuid") }
        }
    }

    fun disconnect() {
        if (bluetoothGatt == null) {
            Log.d(TAG, "disconnect() called but GATT is already null or never connected.")
            mainHandler.post { bleDataListener?.onDebugMessage("disconnect요청 (GATT이미null)") }
            // 상태가 이미 DISCONNECTED가 아닐 경우를 대비해 한 번 더 호출할 수도 있지만,
            // onConnectionStateChange 콜백에서 처리하는 것이 일반적
            if (connectionState != BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = BluetoothProfile.STATE_DISCONNECTED
                mainHandler.post { synchronized(listeners){ listeners.forEach { it.onConnectionStateChanged(connectionState, null)}}}
            }
            return
        }
        val deviceAddress = bluetoothGatt?.device?.address ?: "N/A"
        Log.i(TAG, "Requesting GATT disconnection for $deviceAddress")
        mainHandler.post { bleDataListener?.onDebugMessage("disconnect 요청: $deviceAddress") }
        bluetoothGatt?.disconnect()
        // 실제 GATT close는 onConnectionStateChange 콜백에서 STATE_DISCONNECTED 일 때 closeGatt() 호출
    }

    private fun closeGatt() {
        if (bluetoothGatt != null) {
            val deviceAddress = bluetoothGatt?.device?.address ?: "N/A"
            Log.i(TAG, "Closing GATT client for $deviceAddress")
            mainHandler.post { bleDataListener?.onDebugMessage("GATT 리소스 해제 중: $deviceAddress") }
            bluetoothGatt?.close() // GATT 객체 정리
            bluetoothGatt = null   // 참조 제거
            Log.i(TAG, "GATT client resources released for $deviceAddress.")
            mainHandler.post { bleDataListener?.onDebugMessage("GATT 리소스 해제 완료: $deviceAddress") }
        } else {
            Log.d(TAG, "closeGatt() called but bluetoothGatt is already null.")
            mainHandler.post { bleDataListener?.onDebugMessage("closeGatt() 호출 (GATT 이미 null)") }
        }
        // connectionState 및 connectedDevice는 onConnectionStateChange 콜백에서 이미 DISCONNECTED 상태로 처리됨.
    }

    fun isConnected(): Boolean {
        return connectionState == BluetoothProfile.STATE_CONNECTED
    }
}

// ByteArray 확장 함수 (프로젝트 내 다른 곳에 없다면 여기에 유지)
fun ByteArray.toHexString(): String = joinToString(separator = " ") { byte -> String.format("%02X", byte) }
