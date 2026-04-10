package com.example.app

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class BleWaitingActivity : AppCompatActivity() {

    private val targetMac = "54:90:AC:A8:D5:3C"
    private var bleService: BleService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBound = true
            Log.d("BleWaiting", "Service Bound. Starting scan.")
            bleService?.startBleScan(targetMac)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            bleService = null
        }
    }

    private val gattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BleService.ACTION_GATT_CONNECTED -> {
                    Log.d("BleWaiting", "GATT Connected. Moving to next activity.")
                    val nextIntent = Intent(this@BleWaitingActivity, BleConnectedActivity::class.java)
                    startActivity(nextIntent)
                    finish()
                }
                BleService.ACTION_GATT_DISCONNECTED -> {
                    Log.d("BleWaiting", "GATT Disconnected.")
                    // 연결 끊김에 대한 처리 (예: 사용자에게 알림)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_waiting)

        // ⭐ 중요: 서비스 시작 (bindService()보다 먼저 호출)
        // 이 호출이 서비스를 계속 실행되게 만듭니다.
        val serviceIntent = Intent(this, BleService::class.java)
        startService(serviceIntent)

        // 서비스 바인딩
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)

        // 브로드캐스트 리시버 등록
        val filter = IntentFilter().apply {
            addAction(BleService.ACTION_GATT_CONNECTED)
            addAction(BleService.ACTION_GATT_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            this,
            gattUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // 리시버와 서비스 바인딩 해제
        unregisterReceiver(gattUpdateReceiver)
        if (isBound) {
            unbindService(serviceConnection)
        }
    }
}