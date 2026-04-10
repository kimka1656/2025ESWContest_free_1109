package com.example.app.ble // 실제 패키지명으로 변경하세요

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Intent
import android.os.Bundle
import android.util.Log
// import android.widget.Button // Button import 제거
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R // 실제 R 클래스 경로로 변경하세요
import com.example.app.exercise.ExerciseManager
import com.example.app.exercise.ExerciseItem // ExerciseManager.getCurrentExercise()의 반환 타입이므로 import 필요
import com.example.app.exercise.SessionState // ⭐ 올바른 SessionState import
import com.example.app.exercise.ExerciseSetActivity
 import com.example.app.exercise.ExerciseResultActivity // ⭐ 운동 완료 화면 Activity import 추가 (실제 경로에 맞게)

class DeviceControlActivity : AppCompatActivity(), BleConnectionManager.BleConnectionListener {

    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceAddress: TextView
    private lateinit var tvConnectionState: TextView
    private lateinit var infoTextView: TextView

    private var deviceName: String? = null
    private var deviceAddress: String? = null

    private var pendingScreenTransitionToExerciseSet: Boolean = false

    companion object {
        private const val TAG = "DeviceControlActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)

        deviceName = intent.getStringExtra(BleScanActivity.EXTRA_DEVICE_NAME)
        deviceAddress = intent.getStringExtra(BleScanActivity.EXTRA_DEVICE_ADDRESS)

        if (deviceAddress == null) {
            Log.e(TAG, "Device address is null. Finishing activity.")
            Toast.makeText(this, "기기 주소를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvDeviceAddress = findViewById(R.id.tvDeviceAddress)
        tvConnectionState = findViewById(R.id.tvConnectionState)
        infoTextView = findViewById(R.id.info)

        tvDeviceName.text = "Device Name: ${deviceName ?: "Unknown"}"
        tvDeviceAddress.text = "Address: $deviceAddress"
        tvConnectionState.text = "Status: Initializing..."
        infoTextView.text = "블루투스 기기 초기화 중..."

        BleConnectionManager.registerListener(this)

        if (BleConnectionManager.connectedDevice?.address == deviceAddress && BleConnectionManager.isConnected()) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTED)
        } else if (BleConnectionManager.connectionState == BluetoothProfile.STATE_CONNECTING && BleConnectionManager.connectedDevice?.address == deviceAddress) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTING)
        } else {
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BleConnectionManager.unregisterListener(this)
    }

    private fun updateConnectionState(newState: Int) {
        runOnUiThread {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    tvConnectionState.text = "Status: Connected"
                    if (ExerciseManager.state != SessionState.FINISHED) { // 완료 상태가 아니면 대기 메시지
                        infoTextView.text = "디바이스의 시작 신호 대기 중..."
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    tvConnectionState.text = "Status: Disconnected"
                    if (ExerciseManager.state == SessionState.FINISHED) {
                        // 이미 완료 화면으로 넘어갔거나 갈 예정이므로 특별한 메시지 변경 안함
                    } else {
                        infoTextView.text = "연결 끊김. 다시 시도해주세요."
                    }
                    pendingScreenTransitionToExerciseSet = false
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    tvConnectionState.text = "Status: Connecting..."
                    infoTextView.text = "디바이스에 연결 중..."
                }
            }
        }
    }

    // ⭐ 새로운 헬퍼 함수
    private fun navigateToCompleteScreenAndFinish() {
        Log.i(TAG, "Navigating to exercise complete screen.")
        infoTextView.text = "모든 운동 완료! 결과 화면으로 이동합니다." // 화면 전환 직전 메시지 업데이트

        // TODO: 'ExerciseCompleteActivity::class.java'를 실제 운동 완료 화면 Activity로 교체하세요.
        // 만약 해당 Activity가 없다면 새로 만들어야 합니다.
        // 예시: val intent = Intent(this@DeviceControlActivity, com.example.app.ui.ExerciseCompleteActivity::class.java)
        val intent = Intent(this@DeviceControlActivity, /* ExerciseCompleteActivity::class.java */
            ExerciseResultActivity::class.java)
        // 필요하다면 운동 결과 등의 데이터를 Intent에 추가하여 전달할 수 있습니다.
        // intent.putExtra("totalExerciseTime", ExerciseManager.getTotalTime())
        startActivity(intent)

        if (BleConnectionManager.isConnected()) {
            Log.i(TAG, "Disconnecting BLE as all exercises are finished (navigating away).")
            BleConnectionManager.disconnect() // BLE 연결 해제
        }
        finish() // DeviceControlActivity 종료
    }


    override fun onConnectionStateChanged(newState: Int, gatt: BluetoothGatt?) {
        if (gatt?.device?.address == deviceAddress) {
            updateConnectionState(newState)
        } else if (deviceAddress != null && newState == BluetoothProfile.STATE_DISCONNECTED && gatt?.device?.address == BleConnectionManager.connectedDevice?.address) {
            // This case might be if another part of the app disconnects the device.
        } else if (deviceAddress == null && newState == BluetoothProfile.STATE_DISCONNECTED) {
            updateConnectionState(newState)
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    Toast.makeText(this, "서비스 발견 완료 ($deviceName)", Toast.LENGTH_SHORT).show()
                    if (ExerciseManager.state != SessionState.FINISHED) {
                        infoTextView.text = "서비스 발견! 디바이스 시작 신호 대기 중..."
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "서비스 발견 실패: $status", Toast.LENGTH_SHORT).show()
                    infoTextView.text = "서비스 발견 실패. 연결 확인 필요."
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?, status: Int) {
        if (gatt?.device?.address == deviceAddress &&
            (characteristic?.uuid == BleConnectionManager.TARGET_READ_CHARACTERISTIC_UUID || characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
                runOnUiThread {
                    Toast.makeText(this, "읽은 데이터: $dataString", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "데이터 읽기 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            val charUuid = characteristic?.uuid ?: "Unknown Characteristic"
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(this, "$charUuid 쓰기 성공", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Characteristic $charUuid write successful.")

                    if (charUuid == BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID && pendingScreenTransitionToExerciseSet) {
                        Log.d(TAG, "ACK_START write confirmed, proceeding to ExerciseSetActivity. ExerciseManager state: ${ExerciseManager.state}")
                        if (ExerciseManager.state != SessionState.FINISHED) { // ⭐ 완료 상태가 아닐 때만 운동 화면으로 이동
                            val exerciseSetIntent = Intent(this@DeviceControlActivity, ExerciseSetActivity::class.java)
                            startActivity(exerciseSetIntent)
                            infoTextView.text = "운동 화면으로 이동합니다..."
                        } else {
                            // 이미 FINISHED 상태라면 navigateToCompleteScreenAndFinish가 호출되었거나 호출될 예정
                            Log.d(TAG, "ACK_START write success, but session already FINISHED. Not navigating to ExerciseSetActivity.")
                        }
                        pendingScreenTransitionToExerciseSet = false
                    }
                } else {
                    Toast.makeText(this, "$charUuid 쓰기 실패: $status", Toast.LENGTH_SHORT).show()
                    Log.w(TAG, "Characteristic $charUuid write failed, status: $status")
                    if (charUuid == BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID && pendingScreenTransitionToExerciseSet) {
                        infoTextView.text = "'ACK_START' 전송 실패. 재시도 필요."
                    }
                    pendingScreenTransitionToExerciseSet = false
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, value: ByteArray?) {
        if (gatt?.device?.address == deviceAddress && characteristic?.uuid == BleConnectionManager.TARGET_NOTIFY_CHARACTERISTIC_UUID) {
            val dataString = value?.let { String(it, Charsets.UTF_8) } ?: "N/A"
            Log.i(TAG, "Notification Received: (UTF-8): '$dataString'")

            runOnUiThread {
                if (dataString == "START_REQ") {
                    // 이미 완료된 상태에서 START_REQ를 받으면, 완료 화면으로 이동시키고 더 이상 진행하지 않음.
                    if (ExerciseManager.state == SessionState.FINISHED) {
                        Log.i(TAG, "Received START_REQ but ExerciseManager is ALREADY FINISHED. Navigating to complete screen.")
                        Toast.makeText(this, "모든 운동이 이미 완료되었습니다.", Toast.LENGTH_LONG).show()
                        navigateToCompleteScreenAndFinish()
                        return@runOnUiThread // ⭐ 중요: 더 이상 아래 로직을 실행하지 않음
                    }

                    infoTextView.text = "'START_REQ' 수신! 응답 전송 및 운동 준비..."
                    Log.d(TAG, "Received 'START_REQ'. Initial ExerciseManager state: ${ExerciseManager.state}")

                    pendingScreenTransitionToExerciseSet = false

                    var shouldProceedToExerciseSet = false
                    var exerciseThatWillStart: ExerciseItem? = null
                    var currentRoundTripTime = 0

                    fun handleAllExercisesFinished() {
                        Log.i(TAG, "All exercises finished according to ExerciseManager.")
                        Toast.makeText(this, "모든 운동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                        shouldProceedToExerciseSet = false
                        navigateToCompleteScreenAndFinish() // ⭐ 완료 화면으로 이동 및 현재 Activity 종료
                    }

                    when (ExerciseManager.state) { // 이 시점에는 FINISHED가 아님
                        SessionState.IDLE -> {
                            Log.d(TAG, "ExerciseManager is IDLE. Preparing next exercise.")
                            if (ExerciseManager.prepareAndStartNextExercise()) {
                                shouldProceedToExerciseSet = true
                                exerciseThatWillStart = ExerciseManager.getCurrentExercise()
                                currentRoundTripTime = exerciseThatWillStart?.roundTripTime ?: 0
                                Log.d(TAG, "IDLE -> Next exercise: ${exerciseThatWillStart?.name}, RoundTripTime: $currentRoundTripTime")
                            } else {
                                handleAllExercisesFinished()
                            }
                        }
                        SessionState.WORKING -> {
                            Log.d(TAG, "ExerciseManager is WORKING. START_REQ received. Using current exercise.")
                            exerciseThatWillStart = ExerciseManager.getCurrentExercise()
                            if (exerciseThatWillStart != null) {
                                shouldProceedToExerciseSet = true
                                currentRoundTripTime = exerciseThatWillStart.roundTripTime
                                Log.d(TAG, "WORKING -> Current exercise: ${exerciseThatWillStart.name}, RoundTripTime: $currentRoundTripTime")
                            } else {
                                Log.e(TAG, "ExerciseManager is WORKING but getCurrentExercise() is null. Treating as finished.")
                                handleAllExercisesFinished()
                            }
                        }
                        SessionState.RESTING -> {
                            Log.w(TAG, "Received START_REQ while ExerciseManager is RESTING. Finishing rest and preparing next exercise.")
                            ExerciseManager.finishRest()
                            if (ExerciseManager.prepareAndStartNextExercise()) {
                                shouldProceedToExerciseSet = true
                                exerciseThatWillStart = ExerciseManager.getCurrentExercise()
                                currentRoundTripTime = exerciseThatWillStart?.roundTripTime ?: 0
                                Log.d(TAG, "RESTING -> Next exercise: ${exerciseThatWillStart?.name}, RoundTripTime: $currentRoundTripTime")
                            } else {
                                handleAllExercisesFinished()
                            }
                        }
                        SessionState.FINISHED -> {
                            // 위에서 이미 처리했으므로 이 블록은 실행되지 않아야 함.
                            // 만약의 경우를 대비해 로그만 남김.
                            Log.w(TAG, "Logic Error: Should have been handled if state was FINISHED at START_REQ entry.")
                            navigateToCompleteScreenAndFinish()
                        }
                    }

                    if (shouldProceedToExerciseSet && exerciseThatWillStart != null) {
                        Log.i(TAG, "Target roundTripTime for '${exerciseThatWillStart.name}': $currentRoundTripTime seconds.")
//                        val responseToDevice = "ACK_START"
                        // 만약 목표 시간을 포함하려면:
                         val nonNegativeTargetTime = if (currentRoundTripTime < 0) 0 else currentRoundTripTime
                         val responseToDevice = "[ACK_START,${nonNegativeTargetTime}]"
                        val responseBytes = responseToDevice.toByteArray(Charsets.UTF_8)

                        BleConnectionManager.TARGET_WRITE_CHARACTERISTIC_UUID?.let { writeUuid ->
                            BleConnectionManager.writeCharacteristic(
                                BleConnectionManager.TARGET_SERVICE_UUID,
                                writeUuid,
                                responseBytes,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            )
                            Log.i(TAG, "Attempting to send response to device: '$responseToDevice'")
                        } ?: Log.w(TAG, "TARGET_WRITE_CHARACTERISTIC_UUID is null. Cannot send response.")

                        pendingScreenTransitionToExerciseSet = true
                        Log.d(TAG, "Response send attempt initiated. Will proceed to ExerciseSetActivity after write confirmation.")
                    } else if (shouldProceedToExerciseSet && exerciseThatWillStart == null && ExerciseManager.state != SessionState.FINISHED) {
                        Log.e(TAG, "Error: Should proceed to exercise set, but exerciseThatWillStart is null. State: ${ExerciseManager.state}")
                        infoTextView.text = "운동 정보 오류 발생. 관리자에게 문의하세요."
                    }
                } else {
                    // "START_REQ"가 아닌 다른 데이터 수신 시 처리
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: DeviceControlActivity resumed. ExerciseManager state: ${ExerciseManager.state}")

        // ⭐ onResume 시점에 이미 운동이 완료된 상태인지 먼저 확인
        if (ExerciseManager.state == SessionState.FINISHED) {
            Log.i(TAG, "onResume: Exercises are ALREADY FINISHED. Navigating to complete screen.")
            navigateToCompleteScreenAndFinish()
            return // ⭐ 중요: 더 이상 아래 로직을 실행하지 않음
        }

        pendingScreenTransitionToExerciseSet = false

        if (BleConnectionManager.isConnected() && BleConnectionManager.connectedDevice?.address == deviceAddress) {
            updateConnectionState(BluetoothProfile.STATE_CONNECTED)
            if (ExerciseManager.state == SessionState.IDLE && ExerciseManager.getCurrentExercise() != null) {
                infoTextView.text = "디바이스의 시작 신호 대기 중 ..."
            }
            // FINISHED 상태는 위에서 이미 처리됨
        } else {
            updateConnectionState(BluetoothProfile.STATE_DISCONNECTED)
            // FINISHED 상태는 위에서 이미 처리됨
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        if (gatt?.device?.address == deviceAddress) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(this, "알림/Indication 설정 완료", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "알림/Indication 설정 실패: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

